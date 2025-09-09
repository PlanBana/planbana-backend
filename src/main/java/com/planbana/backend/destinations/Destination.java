package com.planbana.backend.destinations;

import com.planbana.backend.common.BaseEntity;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("destinations")
public class Destination extends BaseEntity {
  private String name;
  private String country;
  private String description;
  private String imageUrl;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getCountry() { return country; }
  public void setCountry(String country) { this.country = country; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
