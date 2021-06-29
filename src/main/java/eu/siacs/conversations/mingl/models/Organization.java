package eu.siacs.conversations.mingl.models;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class Organization {
    @SerializedName("id")
    private String id;

    @SerializedName("key")
    private String key;

    @SerializedName("name")
    private String name;

    @SerializedName("mobile_number")
    private String mobileNumber;

    @SerializedName("email")
    private String email;

    @SerializedName("address")
    private String address;

    @SerializedName("zipcode")
    private String zipcode;

    @SerializedName("status")
    private boolean status = true;
}
