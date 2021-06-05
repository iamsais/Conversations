package eu.siacs.conversations.mingling.api.responses;

import com.google.gson.annotations.SerializedName;
import eu.siacs.conversations.mingling.models.User;

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
