package dev.onyxcat;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountContainer {
    public final String realName;
    public final String accountName;
    public AccountContainer(@JsonProperty("realName") String realName,@JsonProperty("accountName") String accountName){
        this.realName = realName;
        this.accountName = accountName;
    }
}
