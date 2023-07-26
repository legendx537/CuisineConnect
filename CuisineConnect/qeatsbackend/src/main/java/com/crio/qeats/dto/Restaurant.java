
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

// TODO: CRIO_TASK_MODULE_SERIALIZATION
// Implement Restaurant class.
// Complete the class such that it produces the following JSON during serialization.
// {
// "restaurantId": "10",
// "name": "A2B",
// "city": "Hsr Layout",
// "imageUrl": "www.google.com",
// "latitude": 20.027,
// "longitude": 30.0,
// "opensAt": "18:00",
// "closesAt": "23:00",
// "attributes": [
// "Tamil",
// "South Indian"
// ]
// }

// @AllArgsConstructor
// @NoArgsConstructor
// @Getter
// @Setter
// @ToString

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Restaurant {
  private String restaurantId;
  private String name;
  private String city;
  private String imageUrl;
  private Double latitude;
  private Double longitude;
  private String opensAt;
  private String closesAt;
  private List<String> attributes;
  
}



/*  
    import com.fasterxml.jackson.annotation.JsonIgnore;
  
    * @Data == Equivalent to @Getter , @Setter , @RequiredArgsConstructor , @ToString
    * and @EqualsAndHashCode. 
     These all are not required :-As well have used @Data which do all this
    * task in one
    * 
    * @AllArgsConstructor
    * 
    * @Getter
    * 
    * @Setter
    * 
    * @NoArgsConstructor
*/

