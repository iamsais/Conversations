package eu.siacs.conversations.mingl.models.dao;

import eu.siacs.conversations.mingl.api.responses.LoginResponse;
import eu.siacs.conversations.mingl.models.Organization;
import eu.siacs.conversations.mingl.utils.Constants;
import eu.siacs.conversations.mingl.utils.GeneralUtils;
import com.google.gson.Gson;


public class JsonDao {

    public static String getClientAccessToken() {
        String jsonString = GeneralUtils.getPreferenceValue(Constants.LOGIN_RESPONSE);
        LoginResponse loginResponse = new Gson().fromJson(jsonString, LoginResponse.class);
        if (loginResponse != null) {
            return "Bearer " + loginResponse.getJwt();
        } else {
            return null;
        }
    }

    public static String getUserAccessToken() {
        String jsonString = GeneralUtils.getPreferenceValue(Constants.USER_LOGIN_RESPONSE);
        LoginResponse loginResponse = new Gson().fromJson(jsonString, LoginResponse.class);
        if (loginResponse != null) {
            return "Bearer " + loginResponse.getJwt();
        } else {
            return null;
        }
    }

    public static String getOrganizationId() {
        String jsonString = GeneralUtils.getPreferenceValue(Constants.ORG_RESPONSE);
        Organization organization = new Gson().fromJson(jsonString, Organization.class);
        if (organization != null) {
            return organization.getId();
        } else {
            return null;
        }
    }
}
