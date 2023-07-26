
package com.crio.qeats.repositories;

import com.crio.qeats.models.ItemEntity;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ItemRepository extends MongoRepository<ItemEntity, String> {
  
  @Query("{'name': {$regex: '^?0$', $options: 'i'}}")
  List<ItemEntity> findItemsByExactName(String itemName);
  
  @Query("{'name': {$regex: '.*?0.*', $options: 'i'}}")
  List<ItemEntity> findItemsByInExactName(String searchString);
  
  // It can also be used but we have made use of the mongo template for the 
  // attribute queries
  // please note that you have to pass List<String> 
  // For passing searchString as a List<String> 
  // we have to split the searchString with the help of split
  // to be precise 
  // what we have to do in service layer is 
  // List<String> attributes = searchString.split(" ");
  // then pass it to the findItemsByAttributes method defined in the repository
  // Suggestion :- 
  // but remember for complex query,it is feasable to use mongoTemplate

  // @Query("{'attributes': {$in: ?0, $options: 'i'}}")
  // List<ItemEntity> findItemsByAttributes(List<String> attributes);

}

