package edu.uiuc.ncsa.myproxy.oa4mp.oauth2.claims;

import edu.uiuc.ncsa.security.core.exceptions.GeneralException;
import edu.uiuc.ncsa.security.core.util.DebugUtil;
import edu.uiuc.ncsa.security.core.util.StringUtils;
import edu.uiuc.ncsa.oa4mp.delegation.server.ServiceTransaction;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.server.UnsupportedScopeException;
import edu.uiuc.ncsa.oa4mp.delegation.oa2.server.claims.ClaimSourceConfiguration;
import net.sf.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * A claim source backed by a file system. The file simply contains a JSON object of userids and attributes
 * associated with that id. These are then returned as claims.
 * Alternately, the JSON itself can just be set and used. This is how QDL utilities do it so
 * they can fetch the JSON from a virtual file system.
 * <p>Created by Jeff Gaynor<br>
 * on 10/21/19 at  12:49 PM
 */
public class FSClaimSource extends BasicClaimsSourceImpl {
    public FSClaimSource(ClaimSourceConfiguration config) {
        setConfiguration(config);
    }

    /**
     * The name of the property in the configuration that specifies where the file is that holds
     * the claims for this source.
     */
    public static String FILE_PATH_KEY = "filePath";
    /**
     * This is the name of the key in the claims to use. E.g. setting this to "sub" means the sub claim is used.
     * It defaults to the username in the transaction if not set.
     */

    public static String FILE_CLAIM_KEY = "claimKey";
    /**
     * Boolean-valued claim. If  a user is not found, return a default record. This is useful if, e.g., this source contains
     * a set of capabilites that are applied to more or less every user with a few exception.
     */
    public static String USE_DEFAULT_KEY = "useDefault";

    /**
     * The id in the file that contains the default set of claims. Not that this is ignored unless
     * {@link #USE_DEFAULT_KEY} is set.
     */
    public static String DEFAULT_CLAIM_KEY = "defaultClaim";

    Boolean useDefaultClaims = null;

    public boolean isUseDefaultClaims() {
        if (useDefaultClaims == null) {
            Object rawX = getConfiguration().getProperty(USE_DEFAULT_KEY);
            if (rawX == null) return false; // default
            String rawS = rawX.toString();
            if (StringUtils.isTrivial(rawS)) return false;
            useDefaultClaims = Boolean.parseBoolean(rawS);
        }
        return useDefaultClaims;
    }

    public String getDefaultClaimName() {
        Object raw = getConfiguration().getProperty(DEFAULT_CLAIM_KEY);
        if (raw == null) return null;
        return raw.toString();
    }

    /*
    The test file contains a JSON object of properties, e.g. of the form
    {"userid123":{"foo":"bar","eppn":"fnord@blarg.edu"}}
    In this case, there is exactly one entry for the user with username "userid123" and the two claims
    will be included in the claims object.<br/><br/>
    There is, of course, no reason you cannot have a file with things like group entries in it and search for those
    as long as you can identify a claim to use (which can also mean setting one and later removing it).
     */
    @Override
    protected JSONObject realProcessing(JSONObject claims, HttpServletRequest request, ServiceTransaction transaction) throws UnsupportedScopeException {
        // Finally, we can do something...
        String rawJSON = null;
        try {
            rawJSON = readFile();
        } catch (IOException e) {
            DebugUtil.error(this, "Error reading file \"" + e.getMessage() + "\".", e);
            throw new GeneralException(e);
        }
        JSONObject jsonObject = JSONObject.fromObject(rawJSON);
        JSONObject json;
        if (getConfiguration().getProperty(FILE_CLAIM_KEY) == null) {
            json = jsonObject.getJSONObject(transaction.getUsername());
        } else {
            json = jsonObject.getJSONObject((String) claims.get(getConfiguration().getProperty(FILE_CLAIM_KEY)));
        }
        if (isUseDefaultClaims() && (json == null || json.isEmpty())) {
            if (getDefaultClaimName() == null) {
                throw new GeneralException("ERROR: \"" + DEFAULT_CLAIM_KEY + "\" has not been set.");
            }
            if (jsonObject.containsKey(getDefaultClaimName())) {
                json = jsonObject.getJSONObject(getDefaultClaimName());
            }
        }
        claims.putAll(json);
        claims.remove("comment"); // strip out comment
        return super.realProcessing(claims, request, transaction);
    }

    public void setRawJSON(String rawJSON) {
        this.rawJSON = rawJSON;
    }

    String rawJSON = null;


    protected String readFile() throws IOException {
        Object rawfilePath = getConfiguration().getProperty(FILE_PATH_KEY);
        if (rawfilePath == null) {
            throw new GeneralException("ERROR:No \"" + FILE_PATH_KEY + "\" set for this claim source.");
        }
        String filePath = rawfilePath.toString();
        if (filePath.isEmpty()) {
            throw new GeneralException("ERROR:No \"" + FILE_PATH_KEY + "\" set for this claim source.");
        }

        File f = new File(filePath);
        if (!f.exists()) {
            throw new GeneralException("ERROR:File \"" + f + "\" does not exist on this system.");
        }
        if (!f.isFile()) {
            throw new GeneralException("ERROR: \"" + f + "\" is not a file.");
        }
        if (!f.canRead()) {
            throw new GeneralException("ERROR: \"" + f + "\" cannot be read.");
        }

        FileReader fileReader = new FileReader(f);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        StringBuffer lines = new StringBuffer();
        String inLine = bufferedReader.readLine();
        while (inLine != null) {
            lines.append(inLine + "\n");
            inLine = bufferedReader.readLine();
        }
        bufferedReader.close();
        return lines.toString();
    }

    @Override
    public boolean isRunAtAuthorization() {
        return false; // run this only when access tokens are being created, not in the authorization step.
    }
}
