package edu.uiuc.ncsa.oa2.qdl.storage;

import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.OA2SE;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.loader.OA2ConfigurationLoader;
import edu.uiuc.ncsa.qdl.evaluate.OpEvaluator;
import edu.uiuc.ncsa.qdl.evaluate.SystemEvaluator;
import edu.uiuc.ncsa.qdl.exceptions.QDLException;
import edu.uiuc.ncsa.qdl.expressions.ConstantNode;
import edu.uiuc.ncsa.qdl.expressions.Dyad;
import edu.uiuc.ncsa.qdl.extensions.QDLFunction;
import edu.uiuc.ncsa.qdl.extensions.QDLModuleMetaClass;
import edu.uiuc.ncsa.qdl.extensions.QDLVariable;
import edu.uiuc.ncsa.qdl.state.State;
import edu.uiuc.ncsa.qdl.variables.QDLNull;
import edu.uiuc.ncsa.qdl.variables.QDLStem;
import edu.uiuc.ncsa.security.core.Identifier;
import edu.uiuc.ncsa.security.core.Store;
import edu.uiuc.ncsa.security.core.exceptions.GeneralException;
import edu.uiuc.ncsa.security.core.util.*;
import edu.uiuc.ncsa.oa4mp.delegation.server.storage.ClientStore;
import edu.uiuc.ncsa.oa4mp.delegation.common.storage.TransactionStore;
import edu.uiuc.ncsa.security.storage.data.MapConverter;
import edu.uiuc.ncsa.security.util.configuration.ConfigUtil;
import org.apache.commons.configuration.tree.ConfigurationNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static edu.uiuc.ncsa.security.core.util.StringUtils.isTrivial;

/**
 * The class with the inner classes that do all the work here.
 * <p>Created by Jeff Gaynor<br>
 * on 12/18/20 at  7:05 AM
 */
public class StoreFacade implements QDLModuleMetaClass {
    List<String> types;

    public List<String> getStoreTypes() {
        if (types == null) {
            types = new ArrayList<>();
            types.add(STORE_TYPE_CLIENT);
            types.add(STORE_TYPE_APPROVALS);
            types.add(STORE_TYPE_ADMIN_CLIENT_STORE);
            types.add(STORE_TYPE_PERMISSION_STORE);
            types.add(STORE_TYPE_TRANSACTION);
            types.add(STORE_TYPE_TX_STORE);
        }
        return types;
    }

    public MyLoggingFacade getLogger() {
        return logger;
    }

    public void setLogger(MyLoggingFacade logger) {
        this.logger = logger;
    }

    transient MyLoggingFacade logger = null;
    transient ConfigurationNode configurationNode;
    transient OA2SE environment = null;


    public ConfigurationLoader<? extends AbstractEnvironment> getLoader() {
        return new OA2ConfigurationLoader<OA2SE>(getConfigurationNode(), getLogger());
    }

    public ConfigurationNode getConfigurationNode() {
        return configurationNode;
    }

    public void setConfigurationNode(ConfigurationNode configurationNode) {
        this.configurationNode = configurationNode;
    }


    public OA2SE getEnvironment() throws Exception {
        if (environment == null) {
            environment = (OA2SE) getLoader().load();
        }
        return environment;
    }

    boolean initCalled = false;

    protected void checkInit() {
        if (!initCalled) {
            throw new IllegalStateException("Error: You must call init before calling this function");
        }
    }

    protected void init(String configFile, String cfgName) {
        try {
            setConfigurationNode(ConfigUtil.findConfiguration(configFile, cfgName, "service"));
        } catch (Exception x) {
            if (x instanceof RuntimeException) {
                throw (RuntimeException) x;
            }
            throw new GeneralException("Error initializing client management:" + x.getMessage(), x);
        }
        initCalled = true;
    }


    protected String INIT_NAME = "init";

    public static final String STORE_TYPE_CLIENT = "client";
    public static final String STORE_TYPE_APPROVALS = "client_approval";
    public static final String STORE_TYPE_TRANSACTION = "transaction";
    public static final String STORE_TYPE_TX_STORE = "tx_record";
    public static final String STORE_TYPE_PERMISSION_STORE = "permission";
    public static final String STORE_TYPE_ADMIN_CLIENT_STORE = "admin_client";

    protected String checkInitMessage = "Be sure you have called the " + INIT_NAME + " function first or this will fail.";

    String file = null;
    String cfgName = null;
    String storeType = null;
    String FILE_ARG = "file";
    String NAME_ARG = "name";
    String TYPE_ARG = "type";

    public class InitMethod implements QDLFunction {
        @Override
        public String getName() {
            return INIT_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{0, 1, 3};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {

            if (objects.length == 1) {
                if (!(objects[0] instanceof QDLStem)) {
                    throw new IllegalArgumentException("Error: A single argument must be a stem.");
                }
                QDLStem stem = (QDLStem) objects[0];
                file = stem.getString(FILE_ARG);
                cfgName = stem.getString(NAME_ARG);
                storeType = stem.getString(TYPE_ARG);
            }

            if (objects.length == 3) {
                for (int j = 0; j < objects.length; j++) {
                    if (!(objects[j] instanceof String)) {
                        throw new IllegalArgumentException("Error: argument " + j + " must be a string.");
                    }
                }
                file = (String) objects[0];
                cfgName = (String) objects[1];
                storeType = (String) objects[2];
            }
            // Implicit case of zero args is here too.
            if (isTrivial(file) || isTrivial(cfgName) || isTrivial(storeType)) {
                throw new IllegalArgumentException("Error: Missing argument");
            }

            doSetup();
            return true;
        }

        /*
        module_import('oa2:/qdl/store', 'c')
        c#init('/home/ncsa/dev/csd/config/server-oa2.xml', 'localhost:oa4mp.oa2.mariadb', 'client')

        cli. :=  c#read('localhost:command.line')

        module_import('oa2:/qdl/store', 't')
        t#init('/home/ncsa/dev/csd/config/server-oa2.xml', 'localhost:oa4mp.oa2.mariadb', 'transaction')

         t. := t#search('access_token', '.*165.*');
         t.0

         t23. := t#search('access_token', '.*23.*');
             */
        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            switch (argCount) {
                case 0:
                    doxx.add(getName() + "() - Reinitialize this, usually after saving then loading it, since connections to stores must be recreated. ");

                    break;
                case 1:
                    doxx.add(getName() + "(stem.) - reads the configuration file and then loads the configuration with the given name and store type. ");
                    doxx.add("the stem entries have keys file, name and store_type.");
                    doxx.add("The store_type tells which type of store is to be used.");
                    doxx.add("Store types are " + STORE_TYPE_CLIENT + ", " + STORE_TYPE_APPROVALS + ", " + STORE_TYPE_TRANSACTION + ", " + STORE_TYPE_TX_STORE);

                    break;
                case 3:
                    doxx.add(getName() + "(file, name, store_type) - reads the configuration file and then loads the configuration with the given name and store type. ");
                    break;
                default:
                    return doxx;
            }

            doxx.add("For a first initialization, you may either supply each argument directly or simply pass in a stem with the entries of file, name and store_type.");
            doxx.add("This must be called before any other function.");
            return doxx;
        }
    }

    protected void doSetup() {
        if (isTrivial(file) || isTrivial(cfgName) || isTrivial(storeType)) {
            // case is that init is not called. This should be benign at this point.
            return;
        }
        init(file, cfgName);
        try {
            setStoreAccessor(createAccessor(storeType));
        } catch (Exception x) {
            if (x instanceof RuntimeException) {
                throw (RuntimeException) x;
            }
            // If loading the store blows up,
            throw new QDLException("Error loading store: for file " + file + ", config " + cfgName + ", type " + storeType);
        }
        if (storeAccessor == null) {
            // If there is no such store.
            throw new QDLException("unsupported type for store '" + storeType + "': config file =" + file + ", config name= " + cfgName);
        }
    }

    /**
     * Thanks to the vagaraies of Java non-static inner class inheritence, it is just best if this
     * livesin the encloising class and is called. That means it can be easily (and predictably) overridden.
     *
     * @param storeType
     * @return
     * @throws Exception
     */
    protected QDLStoreAccessor createAccessor(String storeType) throws Exception {
        QDLStoreAccessor storeAccessor = null;
        switch (storeType) {

            case STORE_TYPE_ADMIN_CLIENT_STORE:
                storeAccessor = new QDLStoreAccessor(storeType, getEnvironment().getAdminClientStore(), getEnvironment().getMyLogger());
                storeAccessor.setMapConverter(new AdminClientStemMC(getEnvironment().getAdminClientStore().getMapConverter()));
                break;
            case STORE_TYPE_CLIENT:
                storeAccessor = new QDLStoreAccessor(storeType, getEnvironment().getClientStore(), getEnvironment().getMyLogger());
                storeAccessor.setMapConverter(new ClientStemMC(getEnvironment().getClientStore().getMapConverter()));
                break;
            case STORE_TYPE_APPROVALS:
                storeAccessor = new QDLStoreAccessor(storeType, getEnvironment().getClientApprovalStore(), getEnvironment().getMyLogger());
                MapConverter mc = getEnvironment().getClientApprovalStore().getMapConverter();

                storeAccessor.setMapConverter(new ApprovalStemMC(mc));
                break;
            case STORE_TYPE_TRANSACTION:
                storeAccessor = new QDLStoreAccessor(storeType, (Store) getEnvironment().getTransactionStore(), getEnvironment().getMyLogger());
                storeAccessor.setMapConverter(createTransactionStemMC(getEnvironment().getTransactionStore(), getEnvironment().getClientStore()));
                break;
            case STORE_TYPE_TX_STORE:
                storeAccessor = new QDLStoreAccessor(storeType, getEnvironment().getTxStore(), getEnvironment().getMyLogger());
                storeAccessor.setMapConverter(new TXRStemMC(getEnvironment().getTxStore().getMapConverter(), getEnvironment().getClientStore()));
                break;
            default:
                throw new QDLException("unsupported store '" + storeType + "'");

        }
        return storeAccessor;
    }

    protected TransactionStemMC createTransactionStemMC(TransactionStore transactionStore, ClientStore clientStore) {
        return new TransactionStemMC(transactionStore.getMapConverter(), clientStore);
    }

    public String TO_XML_NAME = "to_xml";

    public class ToXML implements QDLFunction {
        @Override
        public String getName() {
            return TO_XML_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            if (objects.length != 1) {
                throw new IllegalArgumentException("Error: " + getName() + " requires a single argument.");
            }
            if (!(objects[0] instanceof QDLStem)) {
                throw new IllegalArgumentException("Error: " + getName() + " requires a stem argument.");
            }
            QDLStem stem = (QDLStem) objects[0];
            if (stem.isEmpty()) {
                return "";
            }
            // last hurdle, make sure it's not just a list of stems
            if (!stem.isList()) {
                return getStoreAccessor().toXML((QDLStem) objects[0]);
            }
            QDLStem out = new QDLStem();
            for (Object key : stem.keySet()) {
                try {
                    out.putLongOrString(key, getStoreAccessor().toXML((QDLStem) stem.get(key)));
                } catch (Throwable t) {
                    getLogger().warn("Could not convert object to XML:" + t.getMessage(), t);
                    out.putLongOrString(key, QDLNull.getInstance());
                }
            }
            return out;
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "(stem. | [stem0., stem1.,...]) - converts the object(s)  XML (serialization) format.");
            doxx.add("Serialization format is a good way to store, backup or send configurations.");
            doxx.add("If you supply a single stem for an object, that is processed, or you may supply a list of");
            doxx.add("object. The result is either a string (of XML) or a null if the conversion failed.");
            doxx.add("E.g.");
            doxx.add("   x. := clients#to_xml(clients#search('client_id','.*ligo.*))");
            doxx.add("would search for all client ids that contain 'ligo' and serialize them the XML");
            doxx.add("See also: " + FROM_XML_NAME);
            return doxx;
        }
    }

    public class FromXML implements QDLFunction {
        @Override
        public String getName() {
            return FROM_XML_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            if (objects.length != 1) {
                throw new IllegalArgumentException("Error: " + getName() + " requires a single argument.");
            }
            if ((objects[0] instanceof String)) {
                return getStoreAccessor().fromXML((String) objects[0]);
            }
            if (!(objects[0] instanceof QDLStem)) {
                throw new IllegalArgumentException("Error: " + getName() + " requires a string argument or stem of them,.");
            }
            QDLStem arg = (QDLStem) objects[0];
            QDLStem out = new QDLStem();
            for (Object key : arg.keySet()) {
                Object obj = arg.get(key);
                if (obj instanceof String) {
                    out.putLongOrString(key, getStoreAccessor().fromXML((String) obj));
                } else {
                    out.putLongOrString(key, QDLNull.getInstance());
                }

            }
            return out;
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "(xml_doc) - converts a serialized object into a stem.");
            doxx.add("See also: " + TO_XML_NAME);
            return doxx;
        }
    }

    public String FROM_XML_NAME = "from_xml";

    public QDLStoreAccessor getStoreAccessor() {
        return storeAccessor;
    }

    public void setStoreAccessor(QDLStoreAccessor storeAccessor) {
        this.storeAccessor = storeAccessor;
    }

    protected transient QDLStoreAccessor storeAccessor;
    protected String CREATE_NAME = "create";

    public class Create implements QDLFunction {
        @Override
        public String getName() {
            return CREATE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{0, 1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            if (objects.length == 1) {
                if (objects[0] instanceof String) {
                    return getStoreAccessor().create((String) objects[0]);
                }
                throw new IllegalArgumentException("Error: The argument must be a string identifier.");
            }
            return getStoreAccessor().create(null);
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            if (argCount == 0) {
                doxx.add(getName() + "() - Create a new blank object of this type.");
            }
            if (argCount == 1) {
                doxx.add(getName() + "(id) - Create a new blank object of this type with the given identifier.");
            }
            doxx.add("You must save this object for it to be in the store.");
            return doxx;
        }
    }

    protected String READ_NAME = "read";

    public class ReadObject implements QDLFunction {
        @Override
        public String getName() {
            return READ_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();
            try {
                QDLStem QDLStem = getStoreAccessor().get(BasicIdentifier.newID(objects[0].toString()));
                if (QDLStem.isEmpty()) {
                    return QDLNull.getInstance();
                }
                return QDLStem;
            } catch (Throwable t) {
                t.printStackTrace();
                throw new QDLException("Error: Could not find the object with id \"" + objects[0].toString() + "\"");
            }
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "(id) - read the stored object with the given identifier. This will return a stem representation. ");
            doxx.add("You may have several active objects at once.");
            doxx.add("If there is no such element, a null will be returned");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    protected String UPDATE_NAME = "update";

    public class UpdateObject implements QDLFunction {
        @Override
        public String getName() {
            return UPDATE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();
            if (!(objects[0] instanceof QDLStem)) {
                throw new IllegalArgumentException("Error: The argument must be a stem variable");
            }
            QDLStem QDLStem = (QDLStem) objects[0];
            List<Boolean> out = getStoreAccessor().saveOrUpdate(QDLStem, false);
            if (out.size() == 0) {
                return Boolean.FALSE;
            }
            if (out.size() == 1) {
                return out.get(0);
            }
            QDLStem QDLStem1 = new QDLStem();
            QDLStem1.addList(out);
            return QDLStem1;
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + " (stem.) updates and existing object in the store. If the object does not exist, this will fail.");
            doxx.add("See also: " + SAVE_NAME);
            return doxx;
        }
    }

    protected String SAVE_NAME = "save";

    public class SaveObject implements QDLFunction {
        @Override
        public String getName() {
            return SAVE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1};
        }

        // cm#init('${cfg_file}', '${cfg_name}')
        //  client. := cm#read('${id}')
        // cm#save(client.)
        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();
            if (!(objects[0] instanceof QDLStem)) {
                throw new IllegalArgumentException("Error: The argument must be a stem variable");
            }
            QDLStem QDLStem = (QDLStem) objects[0];
            List<Boolean> out = getStoreAccessor().saveOrUpdate(QDLStem, true);
            if (out.size() == 0) {
                return Boolean.FALSE;
            }
            if (out.size() == 1) {
                return out.get(0);
            }
            QDLStem QDLStem1 = new QDLStem();
            QDLStem1.addList(out);
            return QDLStem1;
        }


        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "(obj.) - save the object to the store. This returns true if the operation succeeds.");
            doxx.add(getName() + "This may also be a list of stems and each will be saved if possible. Be sure you send along what you want!");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    protected String SEARCH_NAME = "search";

    public class Search implements QDLFunction {
        @Override
        public String getName() {
            return SEARCH_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{2};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();
            String key = objects[0].toString();
            String regex = objects[1].toString();
            return getStoreAccessor().search(key, regex, true);
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "(key, regex) -  search for all clients with the given key whose values satisfy the regex.");
            doxx.add("Note #1: This returns a bunch of stems, one for each object that is found, so it is equivalent to a multi-read");
            doxx.add("Note #2: This may be a huge result if the regex is too general. Do be careful.");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    protected String SIZE_NAME = "size";

    public class Size implements QDLFunction {
        @Override
        public String getName() {
            return SIZE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{0, 1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();
            boolean includeVersions = false;
            if (objects.length == 1) {
                if (objects[0] instanceof Boolean) {
                    includeVersions = (Boolean) objects[0];
                } else {
                    throw new IllegalArgumentException("The first argument of " + SIZE_NAME + ", if present, must be a boolean.");
                }
            }
            try {
                return getStoreAccessor().size(includeVersions);
            } catch (Exception e) {
                throw new QDLException("Error: COuld not determine the size of the store:" + e.getMessage(), e);
            }
        }


        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "({includeVersion}) - returns a count of how many clients there are in this store.");
            doxx.add(getName() + "includeVersions will count the versions in the store too if true, and ignore them if false.");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    protected String KEYS_NAME = "keys";

    public class Keys implements QDLFunction {
        @Override
        public String getName() {
            return KEYS_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{0, 1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            //checkInit();

            if (objects.length == 1) {
                if ((objects[0] instanceof Boolean)) {
                    if ((Boolean) objects[0]) {
                        return getStoreAccessor().getStoreKeys().identifier();
                    }
                } else {
                    throw new IllegalArgumentException(getName() + " requires a boolean as its argument if present");
                }
            }
            return getStoreAccessor().listKeys();
        }


        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            switch (argCount) {
                case 0:
                    doxx.add(getName() + "() - list the keys (names of properties) for the objects in the store.");
                    break;
                case 1:
                    doxx.add(getName() + "(show_primary_key) - list the primary keys this store.");
                    doxx.add("show_primary_key - boolean, if true show the key, if false, show all keys");
                    break;

            }
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    protected String REMOVE_NAME = "remove";

    public class Remove implements QDLFunction {
        @Override
        public String getName() {
            return REMOVE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();
            if (objects.length != 1) {
                throw new IllegalArgumentException("Error: You must specify an identifier for " + REMOVE_NAME);
            }
            String id = null;
            if (objects[0] instanceof String) {
                id = (String) objects[0];
            }
            if (objects[0] instanceof QDLStem) {
                QDLStem QDLStem = (QDLStem) objects[0];
                if (QDLStem.containsKey(getStoreAccessor().getStoreKeys().identifier())) {
                    id = (String) QDLStem.get(getStoreAccessor().getStoreKeys().identifier());
                } else {
                    throw new IllegalArgumentException("Error: The stem does not contain the key \"" + getStoreAccessor().getStoreKeys().identifier() + "\", hence there is no unique identifier given.");
                }
            }
            if (isTrivial(id)) {
                throw new IllegalArgumentException("Error: argument must be a string for " + REMOVE_NAME);
            }

            try {
                return getStoreAccessor().remove(BasicIdentifier.newID(id));
            } catch (Throwable e) {
                throw new QDLException("Error: Could not remove object with id " + objects[0] + ":" + e.getMessage());
            }
        }


        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "(id) - remove the client with the given identifier. Returns true if this worked.");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    public String STORE_TYPES_STEM_NAME = "store_types.";

    public class StoreTypes implements QDLVariable {
        QDLStem storeTypes = null;

        @Override
        public String getName() {
            return STORE_TYPES_STEM_NAME;
        }

        @Override
        public Object getValue() {
            if (storeTypes == null) {
                storeTypes = new QDLStem();
                storeTypes.addList(getStoreTypes());

            }
            return storeTypes;
        }
    }

    public String STORE_HELP_NAME = "store_help";

    public class FacadeHelp implements QDLVariable {
        @Override
        public String getName() {
            return STORE_HELP_NAME;
        }


        String help = null;


        @Override
        public Object getValue() {
            if (help == null) {
                StringBuffer s = new StringBuffer();
                s.append("The store module(s) allow you to access OA4MP stores in QDL. Supported stores are\n");
                s.append(STORE_TYPE_CLIENT + " for clients.\n");
                s.append(STORE_TYPE_APPROVALS + "  for approvals of clients (including admin clients)\n");
                s.append(STORE_TYPE_PERMISSION_STORE + " for managing the permissions between admins and their clients. Use the p_store module\n");
                s.append(STORE_TYPE_ADMIN_CLIENT_STORE + " for administrative clients\n");
                s.append(STORE_TYPE_TX_STORE + " for tokens created by the exchange endpoint (RFC 8693)\n");
                s.append(STORE_TYPE_TRANSACTION + " for pending transactions.\n");
                s.append("There is a list of these in the variable " + STORE_TYPES_STEM_NAME + " \n");
                s.append("\n" + "Every store allows for the following commands (online help is available for these in the workspace\n");
                s.append("using e.g.\n");
                s.append("\n)help p_store#init\n");
                s.append("\n" + FROM_XML_NAME + " Take and object's XML serialization (as a string, e.g. in a file) and convert to a stem.\n");
                s.append(INIT_NAME + " initialize the store. You cannot use a store until you call this.\n");
                s.append(KEYS_NAME + " Lists the keys possible in the stem for this store.\n");
                s.append(READ_NAME + " Read an object from the store by identifier. Note the result is stem\n");
                s.append(REMOVE_NAME + " Remove an object from the store by identifier.\n");
                s.append(SAVE_NAME + " Save an object to the store. If the object exists, it is updated, otherwise a new object is created in the store.\n");
                s.append(SEARCH_NAME + " Search the store by key and value for all objects. This returns a list of stems (which may be huge -- plan your queries!)\n");
                s.append(SIZE_NAME + " The size of the store, i.e., number of objects in the store\n");
                s.append(TO_XML_NAME + " Serialize a stem object into XML (as a string).\n");
                s.append(UPDATE_NAME + " Updates an existing object. This will fail if the object does not exist.\n");
                s.append("*" + PermissionStoreFacade.ADMINS_NAME + " Lists the admins for a given client\n");
                s.append("*" + PermissionStoreFacade.CLIENTS_NAME + " Lists the clients for a given admin\n");
                s.append("*" + PermissionStoreFacade.CLIENT_COUNT_NAME + " Return the number of clients this associated with the admin.\n");
                s.append("* = only available in permission stores.\n");
                s.append("\nNote that you should create a module for each type of store you want and each store using the " + SystemEvaluator.MODULE_IMPORT + " command\n");
                s.append("The alias should be descriptive, so something like 'client' or 'trans' and yes, you may have as many\n");
                s.append("imports as you like talking to different stores -- this is an easy way to move objects from one store to another\n");
                s.append("\n");
                s.append("\n");
                help = s.toString();
            }
            return help;
        }
    }

    protected QDLStem convertArgsToVersionIDs(Object[] objects, String name) {
        QDLStem out = null;
        if (2 < objects.length) {
            throw new IllegalArgumentException("too many arguments for " + name + ".");
        }
        if (objects.length == 2) {
            out = new QDLStem();
            QDLStem id = new QDLStem();
            if (!(objects[0] instanceof String)) {
                throw new IllegalArgumentException("dyadic " + name + " requires a string as its first argument");
            }
            id.put(0L, objects[0]);
            if (!(objects[1] instanceof Long)) {
                throw new IllegalArgumentException("dyadic " + name + " requires an integer as its second argument");
            }
            id.put(1L, objects[1]);
            out.put(0L, id);
            return out;
        }
        // So a single argument
        if (!(objects[0] instanceof QDLStem)) {
            throw new IllegalArgumentException("monadic " + name + " requires stem as its argument");
        }

        QDLStem temp = (QDLStem) objects[0];
        if (temp.isList() && temp.size() == 2) {
            if ((temp.get(0L) instanceof String) && (temp.get(1L) instanceof Long)) {
                out = new QDLStem();
                out.put(0L, temp);
            }
        }
        return (QDLStem) objects[0]; // It was the right format
    }

    protected String VERSION_CREATE_NAME = "v_create";

    /**
     * Create the archived version of an object. There are several cases.
     */
    public class VCreate implements QDLFunction {
        @Override
        public String getName() {
            return VERSION_CREATE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();
            QDLStem arg;
            switch (objects.length) {
                case 1:
                    if (objects[0] instanceof QDLStem) {
                        arg = (QDLStem) objects[0];
                    } else {
                        if (objects[0] instanceof String) {
                            arg = new QDLStem();
                            arg.put(0L, objects[0]);
                        } else {
                            throw new IllegalArgumentException(getName() + " requires stem or string argument");
                        }
                    }
                    break;
                case 0:
                    throw new IllegalArgumentException(getName() + " requires an argument");
                default:
                    throw new IllegalArgumentException(getName() + " requires at most a single argument");
            }
            return getStoreAccessor().archive(arg);
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "(id | ids.) - archive the stored client(s) whose ids are given.");
            doxx.add("Either supply an id for the object or a list of ids. ");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    protected String VERSION_GET_NAME = "v_get";

    public class VGet implements QDLFunction {
        @Override
        public String getName() {
            return VERSION_GET_NAME;
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();

            QDLStem arg = convertArgsToVersionIDs(objects, getName());
            // now this is a list of [id, version] entries.
            QDLStem out = new QDLStem();
            for (Object key : arg.keySet()) {
                VID vid = toVID(arg.get(key));
                if (vid == null) {
                    out.putLongOrString(key, QDLNull.getInstance());
                    continue;
                }
                try {
                    out.putLongOrString(key, getStoreAccessor().getVersion(vid.id, vid.version));
                } catch (Throwable t) {
                    DebugUtil.trace(this, "unable to get version ", t);
                    logger.warn("unable to get version", t);
                    out.putLongOrString(key, QDLNull.getInstance());
                }

            }
            return out;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1, 2};
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            switch (argCount) {
                case 1:
                    doxx.add(getName() + "(id. | ids.) - get the versions for the id. or list of them");
                    break;
                case 2:
                    doxx.add(getName() + "(id, version) - get the version numbered for the identifier");
                    break;
            }
            doxx.add("Versioned ids are either of the form [uri, integer] where the uri is the ");
            doxx.add("identifier of the obeject and version a valid version number.");
            doxx.add("Note that version are numbered starting at 0, so the highest");
            doxx.add("value is the most recent. You may specify the version using a signed");
            doxx.add("number, with -1 meaning the highest number, -2 meaning next highest.");
            doxx.add("(Same as index addressing in QDL.)");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    protected String VERSION_REMOVE_NAME = "v_rm";

    public class VRemove implements QDLFunction {
        @Override
        public String getName() {
            return VERSION_REMOVE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1, 2};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();

            QDLStem args = convertArgsToVersionIDs(objects, getName());
            QDLStem out = new QDLStem();
            for (Object key : args.keySet()) {
                VID vid = toVID(args.get(key));
                if (vid == null) {
                    out.putLongOrString(key, Boolean.FALSE);
                    continue;
                }
                getStoreAccessor().getStoreArchiver().remove(vid.id, vid.version);

                out.putLongOrString(key, Boolean.TRUE);
            }
            return out;
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            switch (argCount) {
                case 1:
                    doxx.add(getName() + "(id. | ids.) - remove the versions for the id. or list of them");
                    break;
                case 2:
                    doxx.add(getName() + "(id, version) - remove the version numbered for the identifier");
                    break;
            }
            doxx.add("This returns a stem of booleans with a true for each removed item and a false otherwise.");
            doxx.add("If you send things that are not version ids, they are ignored.");
            doxx.add("E.g.");
            doxx.add("   " + getName() + "(true, false); // not a validway to identify a version");
            doxx.add("[false]");
            doxx.add("Meaning nothing was done. Note also that a true means that such a thing does not");
            doxx.add("exist in the store any longer, so a true means the entry could");
            doxx.add("be valid but is not there after this returns.");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }


    /**
     * For a stem variable, checks that it is of the form
     * <pre>
     *     [id, version] (in QDL)
     * </pre>
     * and returns an versioned id, {@link VID}.
     * <p>
     * If the argument is not in the right format, a null is returned instead.<br/><br/>
     * This may throw other exceptions if, e.g., the id is not a valid identifier
     *
     * @param QDLStem
     * @return
     */
    protected VID toVID(QDLStem QDLStem) {
        if (QDLStem.size() != 2 || !QDLStem.isList()) {
            return null;
        }
        Object rawID = QDLStem.get(0L);
        if (!(rawID instanceof String)) {
            return null;
        }
        Identifier id = BasicIdentifier.newID(rawID.toString());
        Object v = QDLStem.get(1L);
        if (!(v instanceof Long)) {
            return null;
        }

        return new VID(id, (Long) v);
    }

    public class VID {
        Identifier id;
        Long version;

        public VID(Identifier id, Long version) {
            this.id = id;
            this.version = version;
        }
    }

    protected VID toVID(Object obj) {
        if (!(obj instanceof QDLStem)) {
            return null;
        }
        return toVID((QDLStem) obj);
    }

    protected String VERSION_GET_VERSIONS_NAME = "v_versions";

    public class VGetVersions implements QDLFunction {
        @Override
        public String getName() {
            return VERSION_GET_VERSIONS_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            checkInit();

            if (objects.length != 1) {
                throw new IllegalArgumentException(getName() + " requires a single argument");
            }
            QDLStem args = null;
            boolean hasStringArg = false;
            if (objects[0] instanceof String) {
                args = new QDLStem();
                args.put(0L, objects[0]);
                hasStringArg = true;
            }
            if (objects[0] instanceof QDLStem) {
                args = (QDLStem) objects[0];
            }
            if (args == null) {
                throw new IllegalArgumentException(getName() + " requires either an id or stem of them as its argument.");
            }
            QDLStem out = new QDLStem();
            for (Object key : args.keySet()) {
                Identifier id = toIdentifier(args.get(key));
                if (id == null) {
                    out.putLongOrString(key, QDLNull.getInstance()); // no valid id means a null
                    continue;
                }
                QDLStem entry = new QDLStem();
                entry.addList(getStoreAccessor().getStoreArchiver().getVersionNumbers(id));
                out.putLongOrString(key, entry);
            }
            if (hasStringArg) {
                return out.get(0L); // preserve shape.
            }
            return out;
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + "(id | ids.) - get the versions associated with the id or stem of them.");
            doxx.add("This returns a list for each version numbers available for each identifier.");
            doxx.add("If you submit a stem of them, then each returned valus is a stem. If you submit");
            doxx.add("A single ID, then the result is a simple list.");
            doxx.add("E.g.");
            doxx.add("   " + getName() + "('uri:/my/object');");
            doxx.add("[0,1,3,7]");
            doxx.add("This is the list of valid version numbers for that object");
            doxx.add("   " + getName() + "({'client0':'uri:/my/object0', 'client42':'uri:/my/object42'});");
            doxx.add("{'client0':[1,3],'client42':[0,1,2,3,5]}");
            doxx.add("These are the valid version of each of these.");
            doxx.add(checkInitMessage);
            return doxx;
        }
    }

    protected Identifier toIdentifier(Object obj) {
        if (!(obj instanceof String)) {
            return null;
        }
        try {
            return BasicIdentifier.newID(URI.create(obj.toString()));
        } catch (Throwable t) {
            logger.warn("Could not make identifier for '" + obj + "'", t);
        }
        return null;
    }

    protected String VERSION_RESTORE_NAME = "v_restore";

    public class VRestore implements QDLFunction {
        @Override
        public String getName() {
            return VERSION_RESTORE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{1, 2};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            QDLStem args = convertArgsToVersionIDs(objects, getName());
            QDLStem out = new QDLStem();
            for (Object key : args.keySet()) {
                VID vid = toVID(args.get(key));
                if (vid == null) {
                    out.putLongOrString(key, Boolean.FALSE);
                    continue;
                }
                out.putLongOrString(key, getStoreAccessor().getStoreArchiver().restore(vid.id, vid.version));
            }
            return out;
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            switch (argCount) {
                case 1:
                    doxx.add(getName() + "(id. | ids.) - restore the versions for the id. or list of them");
                    break;
                case 2:
                    doxx.add(getName() + "(id, version) - restore the version numbered for the identifier");
                    break;
            }
            doxx.add("Restores the given version to be to active one.");
            doxx.add("NOTE: This overwrites the currently active object and replaces it!");
            doxx.add("Good practice is to version whatever you are going to restore.");
            doxx.add("");
            doxx.add("");
            return doxx;
        }
    }

    protected String DIFFERENCE_NAME = "diff";

    /**
     * Not currently used because it is unclear what the contract should actually be.
     * Leaving this here for future (possible) use. left. == right. actually covers most cases fine.
     */
    public class Diff implements QDLFunction {
        @Override
        public String getName() {
            return DIFFERENCE_NAME;
        }

        @Override
        public int[] getArgCount() {
            return new int[]{2};
        }

        @Override
        public Object evaluate(Object[] objects, State state) {
            if (objects.length != 2) {
                throw new IllegalArgumentException(getName() + " requires two arguments");
            }
            if (!(objects[0] instanceof QDLStem) || !(objects[1] instanceof QDLStem)) {
                throw new IllegalArgumentException(getName() + " requires both arguments be stems");
            }

            QDLStem left = (QDLStem) objects[0];
            QDLStem right = (QDLStem) objects[1];

            QDLStem out = new QDLStem();
            HashMap<Identifier, QDLStem> baseObjects = new HashMap<>();
            for (Object key : left.keySet()) {
                if (right.containsKey(key)) {
                    Dyad eq = new Dyad(OpEvaluator.EQUALS_VALUE);
                    eq.setLeftArgument(new ConstantNode(left.get(key)));
                    eq.setRightArgument(new ConstantNode(right.get(key)));
                    out.putLongOrString(key, eq.evaluate(state));
                } else {
                    out.putLongOrString(key, QDLNull.getInstance());
                }
            }
            return out;
        }

        @Override
        public List<String> getDocumentation(int argCount) {
            List<String> doxx = new ArrayList<>();
            doxx.add(getName() + " (left., right.) - give differences between left. and right.");
            doxx.add("This is left conformable. Missing elements on right are returned with a null");
            doxx.add("value. If the value on the right is found, then it is compared and a boolean is returned");
            return doxx;
        }
    }

}
