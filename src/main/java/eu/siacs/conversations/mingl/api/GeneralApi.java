package eu.siacs.conversations.mingl.api;

import eu.siacs.conversations.mingl.api.responses.GeneralResponse;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface GeneralApi {

    @GET("/api/token/valid")
    @Headers({"Content-Type: application/json"})
    Call<GeneralResponse> checkTokenValid();

    @Streaming
    @GET
    Call<ResponseBody> downloadGoogleProfilePhoto(@Url String url);
}
