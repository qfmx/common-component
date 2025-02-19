
package com.guuidea.component.chrome.tool.libs.proxy.cryto;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.guuidea.component.chrome.tool.libs.proxy.utils.Reflection;
import com.guuidea.component.chrome.tool.libs.proxy.utils.Util;


/**
 * Crypt factory
 */
public class CryptFactory {

    private static Logger logger = Logger.getLogger(CryptFactory.class.getName());

    private static final Map<String, String> crypts = new HashMap<String, String>() {{
        putAll(AesCrypt.getCiphers());
        putAll(CamelliaCrypt.getCiphers());
        putAll(BlowFishCrypt.getCiphers());
        putAll(SeedCrypt.getCiphers());
        // TODO: other crypts
    }};

    public static boolean isCipherExisted(String name) {
        return (crypts.get(name) != null);
    }

    public static ICrypt get(String name, String password) {
        try {
            Object obj = Reflection.get(crypts.get(name), String.class, name, String.class, password);
            return (ICrypt)obj;

        } catch (Exception e) {
            logger.info(Util.getErrorMessage(e));
        }

        return null;
    }

    public static List<String> getSupportedCiphers() {
        List sortedKeys = new ArrayList<>(crypts.keySet());
        Collections.sort(sortedKeys);
        return sortedKeys;
    }
}
