package eu.siacs.conversations.mingling.api;

import eu.siacs.conversations.mingling.api.responses.GeneralResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;

public interface GeneralApi {

    @GET("/api/token/valid")
    @Headers({"Content-Type: application/json"})
    Call<GeneralResponse> checkTokenValid();

}
