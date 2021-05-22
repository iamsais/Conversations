package eu.siacs.conversations.mingling.api;

import eu.siacs.conversations.mingling.api.requests.OrganizationRequest;
import eu.siacs.conversations.mingling.models.User;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface OrgApi {

    @POST("/api/organization")
    @Headers({"Content-Type: application/json"})
    Call<String> addOrganization(@Body OrganizationRequest organizationRequest);

    @GET("/api/organization/check/")
    @Headers({"Content-Type: application/json"})
    Call<String> getUsers(@Query("key") String key);

    @POST("/api/users")
    @Headers({"Content-Type: application/json"})
    Call<String> register(@Body User user);
}
