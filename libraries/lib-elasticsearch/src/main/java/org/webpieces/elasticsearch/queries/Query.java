package org.webpieces.elasticsearch.queries;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class Query {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> term;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Match match;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Bool bool;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> wildcard;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("query_string")
    private QueryString queryString;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Nested nested;

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public Map<String, String> getTerm() {
        return term;
    }

    public void setTerm(Map<String, String> term) {this.term = term;}

    public Bool getBool() {
        return bool;
    }

    public void setBool(Bool bool) {
        this.bool = bool;
    }

    public Map<String, String> getWildcard() {
        return wildcard;
    }

    public void setWildcard(Map<String, String> wildcard) {
        this.wildcard = wildcard;
    }

    public QueryString getQueryString() {
        return queryString;
    }

    public void setQueryString(QueryString queryString) {
        this.queryString = queryString;
    }

    public Nested getNested() {
        return nested;
    }

    public void setNested(Nested nested) {
        this.nested = nested;
    }
}
