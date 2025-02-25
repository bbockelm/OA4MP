package edu.uiuc.ncsa.oa4mp.delegation.common.storage;

import edu.uiuc.ncsa.security.storage.data.SerializationKeys;

import java.util.List;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 10/20/16 at  12:57 PM
 */
public class BaseClientKeys extends SerializationKeys {
    String creationTS = "creation_ts";
    String debugOn = "debug_on";
    String email = "email";
    String lastModifiedTS = "last_modified_ts";
    String name = "name";
    String secret = "oauth_client_pubkey";

    public String name(String... x) {
        if (0 < x.length) name = x[0];
        return name;
    }


    public String debugOn(String... x) {
        if (0 < x.length) debugOn = x[0];
        return debugOn;
    }

    public String email(String... x) {
        if (0 < x.length) email = x[0];
        return email;
    }

    public String creationTS(String... x) {
         if (0 < x.length) creationTS = x[0];
         return creationTS;
     }

    public String lastModifiedTS(String... x) {
         if (0 < x.length) lastModifiedTS = x[0];
         return lastModifiedTS;
     }


    public String secret(String... x) {
        if (0 < x.length) secret = x[0];
        return secret;
    }

    @Override
    public List<String> allKeys() {
        List<String> allKeys = super.allKeys();
        allKeys.add(name());
        allKeys.add(email());
        allKeys.add(creationTS());
        allKeys.add(secret());
        allKeys.add(lastModifiedTS());
        allKeys.add(debugOn());
        return allKeys;
    }
}
