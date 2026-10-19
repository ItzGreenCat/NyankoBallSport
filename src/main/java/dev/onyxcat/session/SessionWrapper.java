package dev.onyxcat.session;

import com.fasterxml.jackson.databind.JsonNode;
import dev.onyxcat.network.EnvelopeCrypto;
import dev.onyxcat.network.HeaderIdentity;

class SessionWrapper {
        public int uid;
        public String token;
        public String unid;
        public String name;
        public double weight;
        public String username;
        public JsonNode profile;

        public HeaderIdentity headerInformation;
        public SessionWrapper(){
            this.uid = Session.uid;
            this.token = Session.token;
            this.unid = Session.unid;
            this.name = Session.name;
            this.weight = Session.weight;
            this.username = Session.username;
            this.profile = Session.profile;
            this.headerInformation = Session.headerInformation;
        }
        public void unwarp(){
            Session.uid = this.uid;
            Session.token = this.token;
            Session.unid = this.unid;
            Session.name = this.name;
            Session.weight = this.weight;
            Session.username = this.username;
            Session.profile = this.profile;
            Session.headerInformation = this.headerInformation;
        }
    }
