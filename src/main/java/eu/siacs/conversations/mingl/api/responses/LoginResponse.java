package eu.siacs.conversations.mingl.api.responses;

import eu.siacs.conversations.mingl.models.User;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class LoginResponse {
    @SerializedName("jwt")
    private String jwt;

    @SerializedName("token_type")
    private String tokenType;

    @SerializedName("expires_in")
    private String expiresIn;

    @SerializedName("user")
    private User user;

    @SerializedName("org_id")
    private String orgId;

}
