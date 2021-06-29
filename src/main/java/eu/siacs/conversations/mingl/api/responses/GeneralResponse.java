package eu.siacs.conversations.mingl.api.responses;

import eu.siacs.conversations.mingl.models.ResultBody;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class GeneralResponse {

    @SerializedName("headers")
    private JsonElement headers;

    @SerializedName("body")
    private ResultBody resultBody;

    @SerializedName("statusCode")
    private String statusCode;

    @SerializedName("statusCodeValue")
    private long statusCodeValue;
}
