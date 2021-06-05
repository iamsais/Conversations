package eu.siacs.conversations.mingling.api;

import eu.siacs.conversations.mingling.api.requests.AuthenticationRequest;
import eu.siacs.conversations.mingling.api.responses.GeneralResponse;
import eu.siacs.conversations.mingling.models.User;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface UsersApi {

    @POST("/api/login")
    @Headers({"Content-Type: application/json"})
    Call<GeneralResponse> login(@Body AuthenticationRequest authenticationRequest);

    @POST("/api/login/google")
    @Headers({"Content-Type: application/json"})
    Call<GeneralResponse> googleLogin(@Query("id_token") String idToken,
                                      @Query("email") String email);

    @GET("/api/users/{id}")
    @Headers({"Content-Type: application/json"})
    Call<GeneralResponse> getUsers(@Path("id") String id);

    @POST("/api/users")
    @Headers({"Content-Type: application/json"})
    Call<GeneralResponse> register(@Body User user);

    @GET("/api/users/check")
    @Headers({"Content-Type: application/json"})
    Call<GeneralResponse> checkUserAvailable(@Query("username") String username,
                                             @Query("org_key") String workspace);

}
