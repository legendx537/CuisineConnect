
/*
 *
 * * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.services;

import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class RestaurantServiceImpl implements RestaurantService {

  private static final Double peakHoursServingRadiusInKms = 3.0;
  private static final Double normalHoursServingRadiusInKms = 5.0;
  @Autowired
  private transient RestaurantRepositoryService restaurantRepositoryService;


  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {

    // restaurantsList = new ArrayList<>();
    Double servingRadius = null;
    if ((currentTime.isAfter(LocalTime.of(7, 59, 59))
        && currentTime.isBefore(LocalTime.of(10, 00, 01)))
        || (currentTime.isAfter(LocalTime.of(12, 59, 59))
            && currentTime.isBefore(LocalTime.of(14, 00, 01)))
        || (currentTime.isAfter(LocalTime.of(18, 59, 59))
            && currentTime.isBefore(LocalTime.of(21, 00, 01)))) {
      servingRadius = peakHoursServingRadiusInKms;
    } else {
      servingRadius = normalHoursServingRadiusInKms;
    }

    List<Restaurant> restaurantsList =
        restaurantRepositoryService.findAllRestaurantsCloseBy(getRestaurantsRequest.getLatitude(),
            getRestaurantsRequest.getLongitude(), currentTime, servingRadius);
    log.info(restaurantsList);
    return new GetRestaurantsResponse(restaurantsList);
  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Implement findRestaurantsBySearchQuery. The request object has the search string.
  // We have to combine results from multiple sources:
  // 1. Restaurants by name (exact and inexact)
  // 2. Restaurants by cuisines (also called attributes)
  // 3. Restaurants by food items it serves
  // 4. Restaurants by food item attributes (spicy, sweet, etc)
  // Remember, a restaurant must be present only once in the resulting list.
  // Check RestaurantService.java file for the interface contract.
  @Override 
  public GetRestaurantsResponse findRestaurantsBySearchQuery(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
    
    // return findRestaurantsBySearchQueryMt(getRestaurantsRequest, currentTime);

    List<Restaurant> restaurants = new ArrayList<>();

    Double servingRadius = null;
    if ((currentTime.isAfter(LocalTime.of(7, 59, 59))
        && currentTime.isBefore(LocalTime.of(10, 00, 01)))
        || (currentTime.isAfter(LocalTime.of(12, 59, 59))
            && currentTime.isBefore(LocalTime.of(14, 00, 01)))
        || (currentTime.isAfter(LocalTime.of(18, 59, 59))
            && currentTime.isBefore(LocalTime.of(21, 00, 01)))) {
      servingRadius = peakHoursServingRadiusInKms;
    } else {
      servingRadius = normalHoursServingRadiusInKms;
    }

    if (getRestaurantsRequest.getSearchFor().length() != 0) {
      List<List<Restaurant>> fullListOfRestaurants = new ArrayList<>();

      fullListOfRestaurants.add(new ArrayList<>(restaurantRepositoryService
          .findRestaurantsByName(
          getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
          getRestaurantsRequest.getSearchFor(), currentTime, servingRadius)));

      fullListOfRestaurants
          .add(new ArrayList<>(restaurantRepositoryService.findRestaurantsByItemName(
              getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
              getRestaurantsRequest.getSearchFor(), currentTime, servingRadius)));

      fullListOfRestaurants
          .add(new ArrayList<>(restaurantRepositoryService.findRestaurantsByAttributes(
              getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
              getRestaurantsRequest.getSearchFor(), currentTime, servingRadius)));

      fullListOfRestaurants
          .add(new ArrayList<>(restaurantRepositoryService.findRestaurantsByItemAttributes(
              getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
              getRestaurantsRequest.getSearchFor(), currentTime, servingRadius)));


      for (List<Restaurant> restaurantList : fullListOfRestaurants) {
        for (Restaurant restaurant : restaurantList) {
          restaurants.add(restaurant);
        }
      }
    }

    log.info(restaurants);
    return new GetRestaurantsResponse(restaurants);
  }

  // TODO: CRIO_TASK_MODULE_MULTITHREADING
  // Implement multi-threaded version of RestaurantSearch.
  // Implement variant of findRestaurantsBySearchQuery which is at least 1.5x time faster than
  // findRestaurantsBySearchQuery.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQueryMt(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
    List<Restaurant> restaurants = new ArrayList<>();

    Double servingRadius = null;
    if ((currentTime.isAfter(LocalTime.of(7, 59, 59))
        && currentTime.isBefore(LocalTime.of(10, 00, 01)))
        || (currentTime.isAfter(LocalTime.of(12, 59, 59))
            && currentTime.isBefore(LocalTime.of(14, 00, 01)))
        || (currentTime.isAfter(LocalTime.of(18, 59, 59))
            && currentTime.isBefore(LocalTime.of(21, 00, 01)))) {
      servingRadius = peakHoursServingRadiusInKms;
    } else {
      servingRadius = normalHoursServingRadiusInKms;
    }

    if (getRestaurantsRequest.getSearchFor().length() != 0) {
      Set<String> restaurantsIds = new HashSet<>();

      Future<List<Restaurant>> futureResaturantByNameList = restaurantRepositoryService
            .findRestaurantByNameUsingMultithreading(
            getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
            getRestaurantsRequest.getSearchFor(), currentTime, servingRadius);

      Future<List<Restaurant>> futureResaturantByAttributesList = restaurantRepositoryService
            .findRestaurantsByAttributesUsingMultithreading(
            getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
            getRestaurantsRequest.getSearchFor(), currentTime, servingRadius);


      Future<List<Restaurant>> futureResaturantByItemNameList = restaurantRepositoryService
            .findRestaurantsByItemNameUsingMultithreading(
            getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
            getRestaurantsRequest.getSearchFor(), currentTime, servingRadius);


      Future<List<Restaurant>> futureResaturantByItemAttributesList = restaurantRepositoryService
            .findRestaurantsByItemAttributesUsingMultithreading(
            getRestaurantsRequest.getLatitude(), getRestaurantsRequest.getLongitude(),
            getRestaurantsRequest.getSearchFor(), currentTime, servingRadius);

      
      // Extract List of Resturants From Future Object 
      List<Restaurant> restaurantByNameList = new ArrayList<>();
      List<Restaurant> restaurantByAttributesList = new ArrayList<>();
      List<Restaurant> restaurantByItemNameList = new ArrayList<>();
      List<Restaurant> restaurantByItemAttributesList = new ArrayList<>(); 
      try {
        restaurantByNameList = (futureResaturantByNameList.isDone())
          ? futureResaturantByNameList.get() : null;
        restaurantByAttributesList = (futureResaturantByAttributesList.isDone())
             ? futureResaturantByAttributesList.get() : null;
        restaurantByItemNameList = (futureResaturantByItemNameList.isDone())
          ? futureResaturantByItemNameList.get() : null;
        restaurantByItemAttributesList =
          (futureResaturantByItemAttributesList.isDone())
          ? futureResaturantByItemAttributesList.get() : null;
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      

      // Iterate over each of them and add the restaurant if it is not null and 
      // and not already added to final list( means not present in the set 

      addRestaurants(restaurants, restaurantByNameList, restaurantsIds);
      addRestaurants(restaurants, restaurantByAttributesList, restaurantsIds);
      addRestaurants(restaurants, restaurantByItemNameList, restaurantsIds);
      addRestaurants(restaurants, restaurantByItemAttributesList, restaurantsIds);


    }

    log.info(restaurants);
    return new GetRestaurantsResponse(restaurants);
  }

  private void addRestaurants(List<Restaurant> restaurants,List<Restaurant> restaurantToBeAdded,
      Set<String> restaurantsIds) {
    for (Restaurant restaurant:restaurantToBeAdded) {
      if (!restaurantsIds.contains(restaurant.getRestaurantId()) && restaurant != null) {
        restaurants.add(restaurant);
        restaurantsIds.add(restaurant.getRestaurantId());
      }
    }
  }
}

