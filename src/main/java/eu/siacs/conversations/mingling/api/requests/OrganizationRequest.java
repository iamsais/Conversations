package eu.siacs.conversations.mingling.api.requests;

import lombok.Data;

@Data
public class OrganizationRequest {

    private String key;

    private String name;

    private String mobileNumber;

    private String email;

    private String address;

    private String zipcode;
}
