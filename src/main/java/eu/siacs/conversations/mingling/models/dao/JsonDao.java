package eu.siacs.conversations.mingling.models.dao;

import com.google.gson.Gson;

import eu.siacs.conversations.mingling.api.responses.LoginResponse;
import eu.siacs.conversations.mingling.utils.Constants;
import eu.siacs.conversations.mingling.utils.GeneralUtils;

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
}
