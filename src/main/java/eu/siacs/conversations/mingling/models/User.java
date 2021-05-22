package eu.siacs.conversations.mingling.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import lombok.Data;

@Data
public class User {

    @SerializedName("user_id")
    private String id;

    @SerializedName("first_name")
    private String firstName;

    @SerializedName("last_name")
    private String lastName;

    @SerializedName("display_name")
    private String displayName;

    @SerializedName("user_name")
    private String userName;

    @SerializedName("password")
    private String password;

    @SerializedName("email")
    private String email;

    @SerializedName("mobile_number")
    private String mobileNumber;

    @SerializedName("address")
    private String address;

    @SerializedName("zipcode")
    private String zipcode;

    @SerializedName("active")
    private boolean active = true;

    @SerializedName("roles")
    private List<String> roles;

    @SerializedName("organization")
    private List<String> organizations;
}
