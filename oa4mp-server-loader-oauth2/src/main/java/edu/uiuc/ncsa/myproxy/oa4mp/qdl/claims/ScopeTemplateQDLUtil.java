package edu.uiuc.ncsa.myproxy.oa4mp.qdl.claims;

import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.claims.ScopeTemplateUtil;
import edu.uiuc.ncsa.qdl.extensions.QDLFunction;
import edu.uiuc.ncsa.qdl.state.State;
import edu.uiuc.ncsa.qdl.variables.StemVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>Created by Jeff Gaynor<br>
 * on 1/21/21 at  11:11 AM
 */
public class ScopeTemplateQDLUtil implements QDLFunction {
    @Override
    public String getName() {
        return "resolve_templates";
    }

    @Override
    public int[] getArgCount() {
        return new int[]{3};
    }

    @Override
    public Object evaluate(Object[] objects, State state) {
        // 2 stems and a boolean
        if(objects.length != 3){
            throw new IllegalArgumentException(getName() + " requires 3 arguments");
        }
        StemVariable computedStem =   (StemVariable )objects[0];
        StemVariable requestedStem =   (StemVariable )objects[1];
        Boolean isTX = (Boolean)objects[2];
        List<String> computedScopes = computedStem.getStemList().toJSON();
        List<String> requestedScopes = requestedStem.getStemList().toJSON();
        Collection<String> returnedScopes = ScopeTemplateUtil.doCompareTemplates(computedScopes,requestedScopes,isTX);
        List<String> rc = new ArrayList<>();
        rc.addAll(returnedScopes);
        StemVariable output = new StemVariable();
        output.addList(rc);
        return output;
    }

    @Override
    public List<String> getDocumentation(int argCount) {
        List<String> doxx = new ArrayList<>();
        doxx.add(getName() +"(computed_scopes, requested_scopes, is_token_exchange)");
        doxx.add("computed_scopes = scopes from templates that have been resolved.");
        doxx.add("requested_scopes = the scopes the client has requested.");
        doxx.add("is_token_exchange = true if this is part of a token exchange");
        return doxx;
    }
}
