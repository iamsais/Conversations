package eu.siacs.conversations.mingl.api.requests;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class AuthenticationRequest  {
    @SerializedName("username")
    private String username;

    @SerializedName("password")
    private String password;
}
