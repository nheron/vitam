/*
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2020)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL-C license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at the following URL "https://cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL-C license and that you
 * accept its terms.
 */
package fr.gouv.vitam.common.database.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


/**
 * IndexOK
 */
public class ReindexationOK {

    @JsonProperty("aliasName")
    private String aliasName;

    @JsonProperty("indexName")
    private String indexName;

    @JsonProperty("tenants")
    private List<Integer> tenants;

    @JsonProperty("tenantGroup")
    private String tenantGroup;

    public ReindexationOK() {
        // Empty constructor for deserialization
    }

    public ReindexationOK(String aliasName, String indexName, List<Integer> tenants, String tenantGroup) {
        this.aliasName = aliasName;
        this.indexName = indexName;
        this.tenants = tenants;
        this.tenantGroup = tenantGroup;
    }

    public String getAliasName() {
        return aliasName;
    }

    public ReindexationOK setAliasName(String aliasName) {
        this.aliasName = aliasName;
        return this;
    }

    public String getIndexName() {
        return indexName;
    }

    public ReindexationOK setIndexName(String indexName) {
        this.indexName = indexName;
        return this;
    }

    public List<Integer> getTenants() {
        return tenants;
    }

    public ReindexationOK setTenants(List<Integer> tenants) {
        this.tenants = tenants;
        return this;
    }

    public String getTenantGroup() {
        return tenantGroup;
    }

    public ReindexationOK setTenantGroup(String tenantGroup) {
        this.tenantGroup = tenantGroup;
        return this;
    }
}