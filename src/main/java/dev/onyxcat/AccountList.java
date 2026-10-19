package dev.onyxcat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class AccountList {
    public static AccountArray accounts = new AccountArray();
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    public static void saveAccounts() throws IOException {
        MAPPER.writeValue(new File("accounts.yaml"),accounts);
    }
    public static void loadAccounts() throws IOException {
        File file = new File("accounts.yaml");
        if(file.exists()) {
            accounts = MAPPER.readValue(file, AccountArray.class);
        }
    }
}
