/*
 *
 * * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.ItemEntity;
import com.crio.qeats.models.MenuEntity;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.ItemRepository;
import com.crio.qeats.repositories.MenuRepository;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.data.mongodb.core.MongoTemplate; 
import org.springframework.data.mongodb.core.query.Criteria; 
import org.springframework.data.mongodb.core.query.Query; 
import org.springframework.scheduling.annotation.Async; 
import org.springframework.scheduling.annotation.AsyncResult; 
import org.springframework.scheduling.annotation.EnableAsync; 
import org.springframework.stereotype.Service; 
import redis.clients.jedis.Jedis; 
import redis.clients.jedis.JedisPool;


@Service
@EnableAsync
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {

  @Autowired
  private transient Provider<ModelMapper> modelMapperProvider;

  @Autowired
  private transient RestaurantRepository restaurantRepository;

  @Autowired
  private transient ItemRepository itemRepository;

  @Autowired
  private transient MenuRepository menuRepository;

  @Autowired
  private transient RedisConfiguration redisConfiguration;

  @Autowired
  private transient ObjectMapper objectMapper;

  @Autowired
  private transient MongoTemplate mongoTemplate;


  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objectives:
  // 1. Implement findAllRestaurantsCloseby.
  // 2. Remember to keep the precision of GeoHash in mind while using it as a key.
  // Check RestaurantRepositoryService.java file for the interface contract.
  
  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude, Double longitude,
      LocalTime currentTime, Double servingRadiusInKms) {
    // Check if the cache is availble in the redis cache or not
    // -> isCacheAvailablevailable in RedisConfiguration
    // If cache is available then directly return the data
    // If the cache is not available then we have retrieve the data
    // from the mongoDb Database
    // Then create a Geohash -> generate a geoHash with help of
    // latitude and lobgitude - using generateGeoHash
    // After that we havr to call getJedistPool to a Object of jedis
    // After getting Jedis Pool Object , we put the retrive data in Jedis Pool


    // If Cache is available or not
    // that part is sorted here
    // If it is available then search in the redis cache
    // If the cache is not available then search in mongo repository
    if (redisConfiguration.isCacheAvailable()) {
      return findAllRestaurantsCloseByFromRedisCache(latitude, longitude, currentTime,
          servingRadiusInKms);
    } else {
      return findAllRestaurantsCloseByMongo(latitude, longitude, currentTime, servingRadiusInKms);
    }
  }

  private List<Restaurant> findAllRestaurantsCloseByMongo(Double latitude, Double longitude,
      LocalTime currentTime, Double servingRadiusInKms) {
    List<RestaurantEntity> resataurantEntityList = restaurantRepository.findAll();

    List<Restaurant> restaurants = resataurantEntityList.stream()
        .filter(restaurantEntity -> isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
            latitude, longitude, servingRadiusInKms))
        .limit(100)
        .map(restaurantEntity -> modelMapperProvider.get().map(restaurantEntity, Restaurant.class))
        .collect(Collectors.toList());
    return restaurants;
  }

  private List<Restaurant> findAllRestaurantsCloseByFromRedisCache(Double latitude,
      Double longitude, LocalTime currentTime, Double servingRadiusInKms) {
    // Generate GeoHash with standard preciosion value that is 7
    GeoHash geoHash = generateGeoHash(latitude, longitude);

    // Get the JedisPool
    JedisPool jedisPool = redisConfiguration.getJedisPool();

    // create a list of restaurant to be returned
    List<Restaurant> restaurants = new ArrayList<>();

    // create a Jedis reference
    Jedis jedis = null;
    try {
      // Get the Jedis Object
      jedis = jedisPool.getResource();
      // Get the data from redis cache
      String cacheString = jedis.get(geoHash.toBase32());

      if (cacheString != null) {
        // Convert the Cache to List<Restaurant> using objectMapper
        List<RestaurantEntity> resataurantEntityList =
            objectMapper.readValue(cacheString, new TypeReference<List<RestaurantEntity>>() {});

        // filter restaurant based on open or close currently
        // and within the serving radius or not
        restaurants = resataurantEntityList.stream()
            .filter(restaurantEntity -> isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
                latitude, longitude, servingRadiusInKms))
            .limit(100).map(restaurantEntity -> modelMapperProvider.get().map(restaurantEntity,
                Restaurant.class))
            .collect(Collectors.toList());
      } else {
        restaurants =
            findAllRestaurantsCloseByMongo(latitude, longitude, currentTime, servingRadiusInKms);
        String createStringToStore = "";
        try {
          createStringToStore = objectMapper.writeValueAsString(restaurants);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // set the redis cache with the expiration time
        jedis.setex(geoHash.toBase32(), GlobalConstants.REDIS_ENTRY_EXPIRY_IN_SECONDS,
            createStringToStore);
      }
    } catch (Exception e) {
      throw new RuntimeException("Can not access redis cache", e);
    } finally {
      if (jedis != null) {
        jedis.close();
      }
    }

    return restaurants;
  }

  public GeoHash generateGeoHash(double latitude, double longitude) {
    return GeoHash.withCharacterPrecision(latitude, longitude, GlobalConstants.GEOHASH_PRECISION);
  }

  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objective:
  // 1. Check if a restaurant is nearby and open. If so, it is a candidate to be returned.
  // NOTE: How far exactly is "nearby"?

  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * 
   * 
   */

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose names have an exact or partial match with the search query.
  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    Optional<List<RestaurantEntity>> restaurantEntityListExact =
        restaurantRepository.findRestaurantsByNameExact(searchString);

    Optional<List<RestaurantEntity>> restaurantEntityListInexact =
        restaurantRepository.findRestaurantByName(searchString);

    // We have to take care of teh duplicate also
    Set<String> restaurantIdSet = new HashSet<>();
    List<Restaurant> restaurants = new ArrayList<>();

    if (restaurantEntityListExact.isPresent()) {
      for (RestaurantEntity restaurantEntity : restaurantEntityListExact.get()) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude,
            servingRadiusInKms) && !restaurantIdSet.contains(restaurantEntity.getId())
            && restaurants.size() < 100) {
          restaurants.add(modelMapperProvider.get().map(restaurantEntity, Restaurant.class));
          restaurantIdSet.add(restaurantEntity.getId());
        }
      }
    }

    if (restaurantEntityListInexact.isPresent()) {
      for (RestaurantEntity restaurantEntity : restaurantEntityListInexact.get()) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude,
            servingRadiusInKms) && !restaurantIdSet.contains(restaurantEntity.getId())
            && restaurants.size() < 100) {
          restaurants.add(modelMapperProvider.get().map(restaurantEntity, Restaurant.class));
          restaurantIdSet.add(restaurantEntity.getId());
        }
      }
    }

    return restaurants;
  }


  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants whose attributes (cuisines) intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    // find the restaurant by their attributes
    // we have create one method in the restorant repositiry
    // then call it directly and filter

    // Generate a pattern
    List<Pattern> patterns = Arrays.stream(searchString.split(" "))
        .map(attribute -> Pattern.compile(attribute, Pattern.CASE_INSENSITIVE))
        .collect(Collectors.toList());

    Query queryForTheAttribute = new Query();

    for (Pattern pattern : patterns) {
      queryForTheAttribute.addCriteria(Criteria.where("attributes").regex(pattern));
    }
    List<RestaurantEntity> restaurantEntityList =
        mongoTemplate.find(queryForTheAttribute, RestaurantEntity.class);


    List<Restaurant> restaurants = restaurantEntityList.stream()
        .filter(restaurantEntity -> isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
            latitude, longitude, servingRadiusInKms))
        .limit(100)
        .map(restaurantEntity -> modelMapperProvider.get().map(restaurantEntity, Restaurant.class))
        .collect(Collectors.toList());

    return restaurants;

    // these is the another implemetion which uses -> @Query Anotation
    // Optional<List<RestaurantEntity>> optionalrestaurantEntityList =
    // restaurantRepository.findRestaurantsByAttributes(searchString);

    // // We have to take care of teh duplicate also
    // Set<String> restaurantIdSet = new HashSet<>();
    // List<Restaurant> restaurants = new ArrayList<>();

    // if (optionalrestaurantEntityList.isPresent()) {
    // for (RestaurantEntity restaurantEntity : optionalrestaurantEntityList.get()) {
    // if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime, latitude, longitude,
    // servingRadiusInKms) && !restaurantIdSet.contains(restaurantEntity.getId())
    // && restaurants.size() < 100) {
    // restaurants.add(modelMapperProvider.get().map(restaurantEntity, Restaurant.class));
    // restaurantIdSet.add(restaurantEntity.getId());
    // }
    // }
    // }

    // return restaurants;
  }



  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose names form a complete or partial match
  // with the search query.

  @Override
  public List<Restaurant> findRestaurantsByItemName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    // Form a regex for partial search
    String regex = String.join("|", Arrays.asList(searchString.split(" ")));

    // Get the List of Exact Items
    List<ItemEntity> itemEntitiesExact = itemRepository.findItemsByExactName(searchString);

    // Get the List of Inexact Item
    List<ItemEntity> itemEntitiesInexact = itemRepository.findItemsByInExactName(regex);

    // Get List of itemIds from exactItem List
    List<String> itemIds =
        itemEntitiesExact.stream().map(ItemEntity::getId).collect(Collectors.toList());

    // Get List of itemIds from Inexact Item List
    itemIds
        .addAll(itemEntitiesInexact.stream().map(ItemEntity::getId).collect(Collectors.toList()));

    return getRestaurantListFromItemList(latitude, longitude, currentTime, servingRadiusInKms,
        itemIds);
  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Objective:
  // Find restaurants which serve food items whose attributes intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    // search the item by attributes
    // then collect the ids
    // then search the menuList
    // then extract id
    // then convert to restaurant entity
    // then filter restaurant entity
    // convert to Restaurant class with the help os moddleMApper


    List<Pattern> patterns = Arrays.stream(searchString.split(" "))
        .map(attribute -> Pattern.compile(attribute, Pattern.CASE_INSENSITIVE))
        .collect(Collectors.toList());

    Query query = new Query();
    for (Pattern pattern : patterns) {
      query.addCriteria(Criteria.where("attributes").regex(pattern));
    }

    List<ItemEntity> itemEntities = mongoTemplate.find(query, ItemEntity.class);

    // Get List of itemIds
    List<String> itemIds =
        itemEntities.stream().map(ItemEntity::getId).collect(Collectors.toList());

    return getRestaurantListFromItemList(latitude, longitude, currentTime, servingRadiusInKms,
        itemIds);
  }

  // Get Restaurant List From item Entity List
  private List<Restaurant> getRestaurantListFromItemList(Double latitude, Double longitude,
      LocalTime currentTime, Double servingRadiusInKms, List<String> itemIds) {
    // Now get the List of Menus with the help of itemIds
    Optional<List<MenuEntity>> menuEntityList = menuRepository.findMenusByItemsItemIdIn(itemIds);
    List<MenuEntity> menuEntityList2 = menuEntityList.get();

    // Get List Of Restaurant Ids From MenuEnityList
    List<String> restaurantsIds =
        menuEntityList2.stream().map(MenuEntity::getRestaurantId).collect(Collectors.toList());

    // From List Of Restaurants ids -> find the List of RestaurantEntity
    List<RestaurantEntity> restaurantEntityList = new ArrayList<>();

    for (String restaurantId : restaurantsIds) {
      restaurantEntityList.add(restaurantRepository.findById(restaurantId).get());
    }

    // Filter the List Of RestaurantentityList and map to Restaurant class
    List<Restaurant> restaurants = restaurantEntityList.stream()
        .filter(restaurantEntity -> isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
            latitude, longitude, servingRadiusInKms))
        .limit(100)
        .map(restaurantEntity -> modelMapperProvider.get().map(restaurantEntity, Restaurant.class))
        .collect(Collectors.toList());


    return restaurants;
  }
  
  @Override
  @Async
  public Future<List<Restaurant>> findRestaurantByNameUsingMultithreading(Double latitude,
      Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    List<Restaurant> restaurants = findRestaurantsByName(latitude, longitude, searchString,
        currentTime, servingRadiusInKms);
    return new AsyncResult<List<Restaurant>>(restaurants);
  } 
  
  @Override
  @Async
  public Future<List<Restaurant>> findRestaurantsByAttributesUsingMultithreading(Double latitude,
      Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    List<Restaurant> restaurants = findRestaurantsByAttributes(latitude, longitude, searchString,
        currentTime, servingRadiusInKms);
    return new AsyncResult<List<Restaurant>>(restaurants);
  } 
  
  @Override
  @Async
  public Future<List<Restaurant>> findRestaurantsByItemNameUsingMultithreading(Double latitude,
      Double longitude, String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    List<Restaurant> restaurants = findRestaurantsByItemName(latitude, longitude, searchString,
        currentTime, servingRadiusInKms);
    return new AsyncResult<List<Restaurant>>(restaurants);
  } 
  
  @Override
  @Async
  public Future<List<Restaurant>> findRestaurantsByItemAttributesUsingMultithreading(Double
       latitude, Double longitude, String searchString, LocalTime currentTime,
       Double servingRadiusInKms) {
    List<Restaurant> restaurants = findRestaurantsByItemAttributes(latitude, longitude,
        searchString, currentTime, servingRadiusInKms);
    return new AsyncResult<List<Restaurant>>(restaurants);
  } 

  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * 
   * @return boolean True if restaurant falls within serving radius and is open, false otherwise
   */
  private boolean isRestaurantCloseByAndOpen(RestaurantEntity restaurantEntity,
      LocalTime currentTime, Double latitude, Double longitude, Double servingRadiusInKms) {
    if (isOpenNow(currentTime, restaurantEntity)) {
      return GeoUtils.findDistanceInKm(latitude, longitude, restaurantEntity.getLatitude(),
          restaurantEntity.getLongitude()) < servingRadiusInKms;
    }
    return false;
  }

}

