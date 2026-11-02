package com.jobscraper.scraper.companies.oracle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OracleRecruitingSiteTest {

    private final OracleRecruitingSite site = new OracleRecruitingSite(
            "gm-financial", "fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com", "CX_1", "software development");

    @Test
    void shouldBuildSearchUriMatchingTheCandidateExperienceUi() {
        assertThat(site.searchUri(25, 25).toString()).isEqualTo(
                "https://fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com/hcmRestApi/resources/latest/"
                        + "recruitingCEJobRequisitions?onlyData=true&expand=requisitionList.secondaryLocations"
                        + "&finder=findReqs;siteNumber=CX_1,limit=25,keyword=%22software%20development%22,"
                        + "sortBy=POSTING_DATES_DESC,offset=25");
    }

    @Test
    void shouldBuildDetailUri() {
        assertThat(site.detailUri("261186").toString()).isEqualTo(
                "https://fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com/hcmRestApi/resources/latest/"
                        + "recruitingCEJobRequisitionDetails?expand=all&onlyData=true"
                        + "&finder=ById;Id=%22261186%22,siteNumber=CX_1");
    }

    @Test
    void shouldBuildPublicJobUrl() {
        assertThat(site.publicJobUrl("261186")).isEqualTo(
                "https://fa-exvu-saasfaprod1.fa.ocs.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1/job/261186");
    }

    @Test
    void shouldEncodeFinderDelimitersInKeyword() {
        OracleRecruitingSite tricky = new OracleRecruitingSite("acme", "example.com", "CX_1", "java, spring;boot");

        assertThat(tricky.searchUri(0, 25).toString())
                .contains("keyword=%22java%2C%20spring%3Bboot%22,");
    }

    @Test
    void shouldRejectHostWithSchemeOrPath() {
        assertThatThrownBy(() -> new OracleRecruitingSite("acme", "https://example.com/x", "CX_1", "java"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

