package eu.siacs.conversations.mingling.api.responses;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import lombok.Data;
import eu.siacs.conversations.mingling.models.ResultBody;

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
