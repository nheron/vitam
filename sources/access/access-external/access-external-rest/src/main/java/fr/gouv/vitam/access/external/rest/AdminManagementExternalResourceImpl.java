/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
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
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.access.external.rest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import fr.gouv.vitam.access.external.api.AccessCollections;
import fr.gouv.vitam.access.external.api.AccessExtAPI;
import fr.gouv.vitam.access.external.api.AdminCollections;
import fr.gouv.vitam.access.internal.client.AccessInternalClient;
import fr.gouv.vitam.access.internal.client.AccessInternalClientFactory;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalClientNotFoundException;
import fr.gouv.vitam.access.internal.common.exception.AccessInternalClientServerException;
import fr.gouv.vitam.common.GlobalDataRest;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.error.ServiceName;
import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.exception.AccessUnauthorizedException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamThreadAccessException;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.AccessContractModel;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.security.SanityChecker;
import fr.gouv.vitam.common.server.application.AsyncInputStreamHelper;
import fr.gouv.vitam.common.stream.StreamUtils;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.client.model.AccessionRegisterDetailModel;
import fr.gouv.vitam.functional.administration.client.model.ContextModel;
import fr.gouv.vitam.functional.administration.client.model.FileFormatModel;
import fr.gouv.vitam.functional.administration.client.model.IngestContractModel;
import fr.gouv.vitam.functional.administration.client.model.ProfileModel;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;
import fr.gouv.vitam.functional.administration.common.exception.DatabaseConflictException;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesImportInProgressException;
import fr.gouv.vitam.functional.administration.common.exception.FileRulesNotFoundException;
import fr.gouv.vitam.functional.administration.common.exception.ProfileNotFoundException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;

/**
 * Admin Management External Resource Implement
 */
@Path("/admin-external/v1")
@javax.ws.rs.ApplicationPath("webresources")
public class AdminManagementExternalResourceImpl {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(AdminManagementExternalResourceImpl.class);
    private static final String ACCESS_EXTERNAL_MODULE = "ADMIN_EXTERNAL";
    private static final String CODE_VITAM = "code_vitam";

    /**
     * Constructor
     */
    public AdminManagementExternalResourceImpl() {
        LOGGER.debug("init Admin Management Resource server");
    }

    /**
     * checkDocument
     *
     * @param collection the working collection top check document
     * @param document the document to check
     * @return Response
     */
    @Path("/{collection}")
    @PUT
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkDocument(@PathParam("collection") String collection,
        InputStream document) {
        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        try {
            ParametersChecker.checkParameter("xmlPronom is a mandatory parameter", document);
            try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
                if (AdminCollections.FORMATS.compareTo(collection)) {
                    final Status status = client.checkFormat(document);
                    return Response.status(status).build();
                }
                if (AdminCollections.RULES.compareTo(collection)) {
                    final Status status = client.checkRulesFile(document);
                    return Response.status(status).build();
                }
                return Response.status(Status.NOT_FOUND)
                    .entity(getErrorEntity(Status.NOT_FOUND, "Collection nout found", null)).build();
            } catch (ReferentialException ex) {
                LOGGER.error(ex);
                return Response.status(Status.BAD_REQUEST)
                    .entity(getErrorEntity(Status.BAD_REQUEST, ex.getMessage(), null)).build();
            }
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST).entity(getErrorEntity(Status.BAD_REQUEST, e.getMessage(), null))
                .build();
        } finally {
            StreamUtils.closeSilently(document);
        }
    }

    /**
     * Import a referential document
     *
     * @param headers http headers
     * @param uriInfo used to construct the created resource and send it back as location in the response
     * @param collection target collection type
     * @param document inputStream representing the data to import
     * @return The jaxRs Response
     */
    @Path("/{collection}")
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importDocument(@Context HttpHeaders headers, @Context UriInfo uriInfo,
        @PathParam("collection") String collection, InputStream document) {
        Integer tenantId = ParameterHelper.getTenantParameter();
        String filename = headers.getHeaderString(GlobalDataRest.X_FILENAME);
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        try {
            ParametersChecker.checkParameter("xmlPronom is a mandatory parameter", document);
            ParametersChecker.checkParameter(collection, "The collection is mandatory");
            try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
                Status status = Status.CREATED;
                if (AdminCollections.FORMATS.compareTo(collection)) {
                    status = client.importFormat(document, filename);
                }
                if (AdminCollections.RULES.compareTo(collection)) {
                    status = client.importRulesFile(document, filename);
                }
                if (AdminCollections.ENTRY_CONTRACTS.compareTo(collection)) {
                    JsonNode json = JsonHandler.getFromInputStream(document);
                    SanityChecker.checkJsonAll(json);
                    status =
                        client.importIngestContracts(JsonHandler.getFromStringAsTypeRefence(json.toString(),
                            new TypeReference<List<IngestContractModel>>() {}));
                }
                if (AdminCollections.ACCESS_CONTRACTS.compareTo(collection)) {
                    JsonNode json = JsonHandler.getFromInputStream(document);
                    SanityChecker.checkJsonAll(json);
                    status = client.importAccessContracts(JsonHandler.getFromStringAsTypeRefence(json.toString(),
                        new TypeReference<List<AccessContractModel>>() {}));
                }
                if (AdminCollections.CONTEXTS.compareTo(collection)) {
                    JsonNode json = JsonHandler.getFromInputStream(document);
                    SanityChecker.checkJsonAll(json);
                    status = client.importContexts(JsonHandler.getFromStringAsTypeRefence(json.toString(),
                        new TypeReference<List<ContextModel>>() {}));
                }
                if (AdminCollections.PROFILE.compareTo(collection)) {
                    JsonNode json = JsonHandler.getFromInputStream(document);
                    SanityChecker.checkJsonAll(json);
                    RequestResponse requestResponse =
                        client.createProfiles(JsonHandler.getFromStringAsTypeRefence(json.toString(),
                            new TypeReference<List<ProfileModel>>() {}));
                    return Response.status(requestResponse.getStatus())
                        .entity(requestResponse).build();
                }
                // Send the http response with no entity and the status got from internalService;
                ResponseBuilder ResponseBuilder = Response.status(status);
                return ResponseBuilder.build();
            } catch (final DatabaseConflictException e) {
                LOGGER.error(e);
                final Status status = Status.CONFLICT;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            } catch (final FileRulesImportInProgressException e) {
                LOGGER.warn(e);
                return Response.status(Status.FORBIDDEN)
                    .entity(getErrorEntity(Status.FORBIDDEN, e.getMessage(), null)).build();
            } catch (final ReferentialException e) {
                LOGGER.error(e);
                return Response.status(Status.BAD_REQUEST)
                    .entity(getErrorEntity(Status.BAD_REQUEST, e.getMessage(), null)).build();
            } catch (InvalidParseOperationException e) {
                return Response.status(Status.BAD_REQUEST)
                    .entity(getErrorEntity(Status.BAD_REQUEST, e.getMessage(), null)).build();
            }
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            final Status status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
        } finally {
            StreamUtils.closeSilently(document);
        }
    }

    /**
     * Import a Profile file document (xsd or rng, ...)
     *
     * @param uriInfo used to construct the created resource and send it back as location in the response
     * @param profileMetadataId id of the profile metadata
     * @param profileFile inputStream representing the data to import
     * @return The jaxRs Response
     */
    @Path("/{collection}/{id:.+}")
    @PUT
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importProfileFile(@Context UriInfo uriInfo, @PathParam("collection") String collection,
        @PathParam("id") String profileMetadataId,
        InputStream profileFile) {
        if (!AdminCollections.PROFILE.compareTo(collection)) {
            LOGGER.error("Endpoint accept only profiles");
            final Status status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, "Endpoint accept only profiles", null))
                .build();
        }
        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        try {
            ParametersChecker.checkParameter("profileFile stream is a mandatory parameter", profileFile);
            ParametersChecker.checkParameter(profileMetadataId, "The profile id is mandatory");
            try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
                RequestResponse requestResponse = client.importProfileFile(profileMetadataId, profileFile);
                ResponseBuilder ResponseBuilder = Response.status(requestResponse.getStatus())
                    .entity(requestResponse);
                return ResponseBuilder.build();
            } catch (final DatabaseConflictException e) {
                LOGGER.error(e);
                final Status status = Status.CONFLICT;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            } catch (final ReferentialException e) {
                LOGGER.error(e);
                return Response.status(Status.BAD_REQUEST)
                    .entity(getErrorEntity(Status.BAD_REQUEST, e.getMessage(), null)).build();
            }
        } catch (final IllegalArgumentException e) {
            LOGGER.error(e);
            final Status status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
        } finally {
            StreamUtils.closeSilently(profileFile);
        }
    }

    /**
     * Download the file (profile file or traceability file)<br/>
     * <br/>
     * <b>The caller is responsible to close the Response after consuming the inputStream.</b>
     *
     * @param collection
     * @param fileId
     * @param asyncResponse
     */
    @GET
    @Path("/{collection}/{id:.+}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public void downloadProfileFileOrTraceabilityFile(@PathParam("collection") String collection,
        @PathParam("id") String fileId,
        @Suspended final AsyncResponse asyncResponse) {

        if (AdminCollections.PROFILE.compareTo(collection)) {
            ParametersChecker.checkParameter("Profile id should be filled", fileId);
            Integer tenantId = ParameterHelper.getTenantParameter();
            VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
            VitamThreadPoolExecutor.getDefaultExecutor()
                .execute(() -> asyncDownloadProfileFile(fileId, asyncResponse));

        } else if (AdminCollections.TRACEABILITY.compareTo(collection)) {
            try {
                ParametersChecker.checkParameter("Traceability operation should be filled", fileId);

                Integer tenantId = ParameterHelper.getTenantParameter();
                VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));

                VitamThreadPoolExecutor.getDefaultExecutor()
                    .execute(() -> downloadTraceabilityOperationFile(fileId, asyncResponse));
            } catch (IllegalArgumentException | VitamThreadAccessException e) {
                LOGGER.error(e);
                final Response errorResponse = Response.status(Status.PRECONDITION_FAILED)
                    .entity(getErrorStream(Status.PRECONDITION_FAILED, e.getMessage(), null))
                    .build();
                AsyncInputStreamHelper.asyncResponseResume(asyncResponse, errorResponse);
            }
        }
        else {
            LOGGER.error("Endpoint accept only profiles");
            AsyncInputStreamHelper.asyncResponseResume(asyncResponse, Response.status(Status.NOT_IMPLEMENTED)
                .entity(getErrorStream(Status.NOT_IMPLEMENTED, "Endpoint accept only profiles", null)).build());
        }
    }

    private void downloadTraceabilityOperationFile(String operationId, final AsyncResponse asyncResponse) {
        AsyncInputStreamHelper helper;

        try (AccessInternalClient client = AccessInternalClientFactory.getInstance().getClient()) {

            final Response response = client.downloadTraceabilityFile(operationId);
            helper = new AsyncInputStreamHelper(asyncResponse, response);
            final ResponseBuilder responseBuilder =
                Response.status(Status.OK)
                    .header("Content-Disposition", response.getHeaderString("Content-Disposition"))
                    .type(response.getMediaType());
            helper.writeResponse(responseBuilder);
        } catch (final InvalidParseOperationException | IllegalArgumentException exc) {
            LOGGER.error(exc);
            final Response errorResponse = Response.status(Status.PRECONDITION_FAILED)
                .entity(getErrorStream(Status.PRECONDITION_FAILED, exc.getMessage(), null))
                .build();
            AsyncInputStreamHelper.asyncResponseResume(asyncResponse, errorResponse);
        } catch (final AccessInternalClientServerException exc) {
            LOGGER.error(exc.getMessage(), exc);
            final Response errorResponse =
                Response.status(Status.INTERNAL_SERVER_ERROR).entity(getErrorStream(Status.INTERNAL_SERVER_ERROR, 
                    exc.getMessage(), null)).build();
            AsyncInputStreamHelper.asyncResponseResume(asyncResponse, errorResponse);
        } catch (final AccessInternalClientNotFoundException exc) {
            LOGGER.error(exc.getMessage(), exc);
            final Response errorResponse =
                Response.status(Status.NOT_FOUND).entity(getErrorStream(Status.NOT_FOUND, exc.getMessage(), null)).build();
            AsyncInputStreamHelper.asyncResponseResume(asyncResponse, errorResponse);
        } catch (AccessUnauthorizedException e) {
            LOGGER.error("Contract access does not allow ", e);
            final Response errorResponse =
                Response.status(Status.UNAUTHORIZED).entity(getErrorStream(Status.UNAUTHORIZED, e.getMessage(), null)).build();
            AsyncInputStreamHelper.asyncResponseResume(asyncResponse, errorResponse);
        }
    }

    private void asyncDownloadProfileFile(String profileMetadataId, final AsyncResponse asyncResponse) {
        try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
            final Response response = client.downloadProfileFile(profileMetadataId);
            final AsyncInputStreamHelper helper = new AsyncInputStreamHelper(asyncResponse, response);
            final ResponseBuilder responseBuilder =
                Response.status(Status.OK)
                    .header("Content-Disposition", response.getHeaderString("Content-Disposition"))
                    .type(response.getMediaType());
            helper.writeResponse(responseBuilder);
        } catch (final ProfileNotFoundException exc) {
            LOGGER.error(exc.getMessage(), exc);
            AsyncInputStreamHelper
                .asyncResponseResume(
                    asyncResponse,
                    Response.status(Status.NOT_FOUND)
                        .entity(getErrorStream(Status.NOT_FOUND, exc.getMessage(), null).toString()).build());
        } catch (final AdminManagementClientServerException exc) {
            LOGGER.error(exc.getMessage(), exc);
            AsyncInputStreamHelper
                .asyncResponseResume(
                    asyncResponse,
                    Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity(getErrorStream(Status.INTERNAL_SERVER_ERROR, exc.getMessage(), null).toString())
                        .build());
        }
    }

    /**
     * findDocuments using get method
     *
     * @param collection the working collection to find document
     * @param select the select query to find document
     * @return Response
     */
    @Path("/{collection}")
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response findDocuments(@Context HttpHeaders headers,
        @PathParam("collection") String collection, JsonNode select) {


        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        try {
            ParametersChecker.checkParameter(collection, "The collection is mandatory");
            try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
                if (AdminCollections.FORMATS.compareTo(collection)) {
                    final RequestResponse<FileFormatModel> result = client.getFormats(select);
                    int st = result.isOk() ? Status.OK.getStatusCode() : result.getHttpCode();
                    return Response.status(st).entity(result).build();
                }
                if (AdminCollections.RULES.compareTo(collection)) {
                    final JsonNode result = client.getRules(select);
                    return Response.status(Status.OK).entity(result).build();
                }
                if (AdminCollections.ENTRY_CONTRACTS.compareTo(collection)) {
                    RequestResponse<IngestContractModel> result = client.findIngestContracts(select);
                    int st = result.isOk() ? Status.OK.getStatusCode() : result.getHttpCode();
                    return Response.status(st).entity(result).build();
                }
                if (AdminCollections.ACCESS_CONTRACTS.compareTo(collection)) {
                    RequestResponse result = client.findAccessContracts(select);
                    int st = result.isOk() ? Status.OK.getStatusCode() : result.getHttpCode();
                    return Response.status(st).entity(result).build();
                }
                if (AdminCollections.PROFILE.compareTo(collection)) {
                    RequestResponse result = client.findProfiles(select);
                    int st = result.isOk() ? Status.OK.getStatusCode() : result.getHttpCode();
                    return Response.status(st).entity(result).build();
                }
                if (AdminCollections.CONTEXTS.compareTo(collection)) {
                    RequestResponse result = client.findContexts(select);
                    int st = result.isOk() ? Status.OK.getStatusCode() : result.getHttpCode();
                    return Response.status(st).entity(result).build();
                }

                if (AdminCollections.ACCESSION_REGISTERS.compareTo(collection)) {
                    final RequestResponse result = client.getAccessionRegister(select);
                    int st = result.isOk() ? Status.OK.getStatusCode() : result.getHttpCode();
                    return Response.status(st).entity(result).build();
                }
                final Status status = Status.NOT_FOUND;
                return Response.status(status).entity(getErrorEntity(status, "Collection not found", null)).build();
            } catch (ReferentialNotFoundException | FileRulesNotFoundException e) {
                final Status status = Status.NOT_FOUND;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            } catch (ReferentialException | IOException e) {
                LOGGER.error(e);
                final Status status = Status.INTERNAL_SERVER_ERROR;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            } catch (final InvalidParseOperationException e) {
                LOGGER.error(e);
                final Status status = Status.BAD_REQUEST;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            } catch (final AccessUnauthorizedException e) {
                LOGGER.error("Access contract does not allow ", e);
                final Status status = Status.UNAUTHORIZED;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error(e);
            final Status status = Status.PRECONDITION_FAILED;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
        }
    }

    /**
     * findDocuments using post method, or handle classical post for creation
     *
     * @param collection the working collection to find document
     * @param select the select query to find document
     * @return Response
     */
    @Path("/{collection}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createOrfindDocuments(@PathParam("collection") String collection, JsonNode select)
        throws DatabaseConflictException {

        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        ParametersChecker.checkParameter("Json select is a mandatory parameter", select);
        ParametersChecker.checkParameter(collection, "The collection is mandatory");
        try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
            Object respEntity = null;
            Status status = Status.CREATED;
            if (AdminCollections.ENTRY_CONTRACTS.compareTo(collection)) {
                SanityChecker.checkJsonAll(select);
                status =
                    client.importIngestContracts(JsonHandler.getFromStringAsTypeRefence(select.toString(),
                        new TypeReference<List<IngestContractModel>>() {}));
            }
            if (AdminCollections.ACCESS_CONTRACTS.compareTo(collection)) {
                SanityChecker.checkJsonAll(select);
                status = client.importAccessContracts(JsonHandler.getFromStringAsTypeRefence(select.toString(),
                    new TypeReference<List<AccessContractModel>>() {}));
            }
            if (AdminCollections.PROFILE.compareTo(collection)) {
                SanityChecker.checkJsonAll(select);
                RequestResponse requestResponse =
                    client.createProfiles(JsonHandler.getFromStringAsTypeRefence(select.toString(),
                        new TypeReference<List<ProfileModel>>() {}));
                return Response.status(requestResponse.getStatus())
                    .entity(requestResponse).build();
            }
            if (AdminCollections.CONTEXTS.compareTo(collection)) {
                SanityChecker.checkJsonAll(select);
                status = client.importContexts(JsonHandler.getFromStringAsTypeRefence(select.toString(),
                    new TypeReference<List<ContextModel>>() {}));
            }

            if (AdminCollections.ACCESSION_REGISTERS.compareTo(collection)) {
                SanityChecker.checkJsonAll(select);
                RequestResponse requestResponse =
                    client.createorUpdateAccessionRegister(JsonHandler.getFromStringAsTypeRefence(select.toString(),
                        new TypeReference<AccessionRegisterDetailModel>() {}));

                return Response.status(requestResponse.getStatus())
                    .entity(requestResponse).build();

            }

            // Send the http response with the entity and the status got from internalService;
            ResponseBuilder ResponseBuilder = Response.status(status)
                .entity(respEntity != null ? respEntity : "Successfully imported");
            return ResponseBuilder.build();
        } catch (final ReferentialException e) {
            LOGGER.error(e);
            return Response.status(Status.BAD_REQUEST)
                .entity(getErrorEntity(Status.BAD_REQUEST, e.getMessage(), null)).build();
        } catch (InvalidParseOperationException e) {
            return Response.status(Status.BAD_REQUEST)
                .entity(getErrorEntity(Status.BAD_REQUEST, e.getMessage(), null)).build();
        }
    }

    /**
     * With Document By Id
     *
     * @param collection
     * @param documentId
     * @return Response
     */
    @Path("/{collection}/{id_document:.+}")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response findDocumentByID(@PathParam("collection") String collection,
        @PathParam("id_document") String documentId) {
        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        return Response.status(Status.BAD_REQUEST)
            .entity(getErrorEntity(Status.BAD_REQUEST, "Method not yet implemented", null)).build();
    }

    /**
     * findDocumentByID
     *
     * @param collection he working collection find check document
     * @param documentId the document id to find
     * @return Response
     */
    @Path("/{collection}/{id_document:.+}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response findDocumentByID(@PathParam("collection") String collection,
        @PathParam("id_document") String documentId, JsonNode select) {
        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        try {
            ParametersChecker.checkParameter("formatId is a mandatory parameter", documentId);
            SanityChecker.checkParameter(documentId);
            try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
                if (AdminCollections.FORMATS.compareTo(collection)) {
                    final JsonNode result = client.getFormatByID(documentId);
                    return Response.status(Status.OK).entity(result).build();
                }
                if (AdminCollections.RULES.compareTo(collection)) {
                    final JsonNode result = client.getRuleByID(documentId);
                    return Response.status(Status.OK).entity(result).build();
                }
                if (AdminCollections.ENTRY_CONTRACTS.compareTo(collection)) {
                    RequestResponse<IngestContractModel> requestResponse = client.findIngestContractsByID(documentId);
                    int st = requestResponse.isOk() ? Status.OK.getStatusCode() : requestResponse.getHttpCode();
                    return Response.status(st).entity(requestResponse).build();
                }
                if (AdminCollections.ACCESS_CONTRACTS.compareTo(collection)) {
                    RequestResponse<AccessContractModel> requestResponse = client.findAccessContractsByID(documentId);
                    int st = requestResponse.isOk() ? Status.OK.getStatusCode() : requestResponse.getHttpCode();
                    return Response.status(st).entity(requestResponse).build();
                }
                if (AdminCollections.PROFILE.compareTo(collection)) {
                    RequestResponse<ProfileModel> requestResponse = client.findProfilesByID(documentId);
                    int st = requestResponse.isOk() ? Status.OK.getStatusCode() : requestResponse.getHttpCode();
                    return Response.status(st).entity(requestResponse).build();
                }
                if (AdminCollections.CONTEXTS.compareTo(collection)) {
                    RequestResponse<ContextModel> requestResponse = client.findContextById(documentId);
                    int st = requestResponse.isOk() ? Status.OK.getStatusCode() : requestResponse.getHttpCode();
                    return Response.status(st).entity(requestResponse).build();
                }
                final Status status = Status.NOT_FOUND;
                return Response.status(status).entity(getErrorEntity(status, "Collection not found", null)).build();
            } catch (ReferentialNotFoundException e) {
                LOGGER.error(e);
                final Status status = Status.NOT_FOUND;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            } catch (final ReferentialException e) {
                LOGGER.error(e);
                final Status status = Status.INTERNAL_SERVER_ERROR;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            } catch (final InvalidParseOperationException e) {
                LOGGER.error(e);
                final Status status = Status.BAD_REQUEST;
                return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
            }
        } catch (final IllegalArgumentException | InvalidParseOperationException e) {
            LOGGER.error(e);
            return Response.status(Status.PRECONDITION_FAILED)
                .entity(getErrorEntity(Status.PRECONDITION_FAILED, e.getMessage(), null)).build();
        }
    }

    /**
     * Update document
     *
     * @param collection
     * @param id
     * @param queryDsl
     * @return Response
     * @throws AdminManagementClientServerException
     * @throws InvalidParseOperationException
     */
    @Path("/{collection}/{id:.+}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateDocument(@PathParam("collection") String collection,
        @PathParam("id") String id, JsonNode queryDsl)
        throws AdminManagementClientServerException, InvalidParseOperationException {
        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        try {
            try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
                RequestResponse response = null;
                if (AdminCollections.CONTEXTS.compareTo(collection)) {
                    response = client.updateContext(id, queryDsl);
                }
                if (AdminCollections.ACCESS_CONTRACTS.compareTo(collection)) {
                    response = client.updateAccessContract(id, queryDsl);
                }
                if (AdminCollections.ENTRY_CONTRACTS.compareTo(collection)) {
                    response = client.updateIngestContract(id, queryDsl);
                }
                if (response != null && response.isOk()) {
                    return Response.status(Status.OK).entity(response).build();
                } else {
                    final VitamError error = (VitamError) response;
                    if (error != null) {
                        return Response.status(error.getHttpCode()).entity(response).build();
                    } else {
                        return Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity(getErrorEntity(Status.INTERNAL_SERVER_ERROR, null, null).toString()).build();
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error(e);
            return Response.status(Status.PRECONDITION_FAILED)
                .entity(getErrorEntity(Status.PRECONDITION_FAILED, e.getMessage(), null)).build();
        }
    }

    /**
     * findDocumentByID
     *
     * @param documentId the document id to get
     * @return Response
     */
    @POST
    @Path(AccessExtAPI.ACCESSION_REGISTERS_API + "/{id_document}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response findAccessionRegisterById(@PathParam("id_document") String documentId) {
        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
        final Status status = Status.NOT_IMPLEMENTED;
        return Response.status(status).entity(getErrorEntity(status, status.getReasonPhrase(), null)).build();
    }


    /**
     * findAccessionRegisterDetail
     *
     * @param documentId the document id of accession register to get
     * @param select the query to get document
     * @return Response
     */
    @GET
    @Path(AccessExtAPI.ACCESSION_REGISTERS_API + "/{id_document}/accession-register-detail")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response findAccessionRegisterDetail(@PathParam("id_document") String documentId, JsonNode select) {
        Integer tenantId = ParameterHelper.getTenantParameter();
        VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));

        ParametersChecker.checkParameter("accession register id is a mandatory parameter", documentId);
        try (AdminManagementClient client = AdminManagementClientFactory.getInstance().getClient()) {
            RequestResponse result =
                client.getAccessionRegisterDetail(documentId, select);
            int st = result.isOk() ? Status.OK.getStatusCode() : result.getHttpCode();

            return Response.status(st).entity(result).build();
        } catch (final ReferentialNotFoundException e) {
            return Response.status(Status.OK).entity(new RequestResponseOK().setHttpCode(Status.OK.getStatusCode()))
                .build();
        } catch (InvalidParseOperationException e) {
            LOGGER.error(e);
            final Status status = Status.BAD_REQUEST;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
        } catch (Exception e) {
            LOGGER.error(e);
            final Status status = Status.INTERNAL_SERVER_ERROR;
            return Response.status(status).entity(getErrorEntity(status, e.getMessage(), null)).build();
        }
    }

    /**
     * Checks a traceability operation
     *
     * @param query the DSLQuery used to find the traceability operation to validate
     * @return The verification report == the logbookOperation
     */
    @POST
    @Path(AccessExtAPI.TRACEABILITY_API + "/check")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkOperationTraceability(JsonNode query) {

        try (AccessInternalClient client = AccessInternalClientFactory.getInstance().getClient()) {
            ParametersChecker.checkParameter("checks operation Logbook traceability parameters", query);

            Integer tenantId = ParameterHelper.getTenantParameter();
            VitamThreadUtils.getVitamSession().setRequestId(GUIDFactory.newRequestIdGUID(tenantId));
            RequestResponse<JsonNode> result = client.checkTraceabilityOperation(query);
            int st = result.isOk() ? Status.OK.getStatusCode() : result.getHttpCode();

            return Response.status(st).entity(result).build();
        } catch (final IllegalArgumentException | InvalidParseOperationException e) {
            LOGGER.error(e);
            final Status status = Status.BAD_REQUEST;
            return Response.status(status).entity(new VitamError(status.name()).setHttpCode(status.getStatusCode())
                .setContext(ServiceName.EXTERNAL_ACCESS.getName())
                .setState("code_vitam")
                .setMessage(status.getReasonPhrase())
                .setDescription(e.getMessage())).build();
        } catch (LogbookClientServerException e) {
            LOGGER.error(e);
            final Status status = Status.INTERNAL_SERVER_ERROR;
            return Response.status(status).entity(new VitamError(status.name()).setHttpCode(status.getStatusCode())
                .setContext(ServiceName.EXTERNAL_ACCESS.getName())
                .setState("code_vitam")
                .setMessage(status.getReasonPhrase())
                .setDescription(e.getMessage())).build();
        } catch (AccessUnauthorizedException e) {
            LOGGER.error("Contract access does not allow ", e);
            final Status status = Status.UNAUTHORIZED;
            return Response.status(status).entity(getErrorEntity(status, e.getLocalizedMessage())).build();
        }
    }

    /**
     * Construct the error following input
     *
     * @param status Http error status
     * @param message The functional error message, if absent the http reason phrase will be used instead
     * @param code The functional error code, if absent the http code will be used instead
     * @return
     */
    private VitamError getErrorEntity(Status status, String message, String code) {
        String aMessage =
            (message != null && !message.trim().isEmpty()) ? message
                : (status.getReasonPhrase() != null ? status.getReasonPhrase() : status.name());
        String aCode = (code != null) ? code : String.valueOf(status.getStatusCode());
        return new VitamError(aCode).setHttpCode(status.getStatusCode()).setContext(ACCESS_EXTERNAL_MODULE)
            .setState(CODE_VITAM).setMessage(status.getReasonPhrase()).setDescription(aMessage);
    }


    private InputStream getErrorStream(Status status, String message, String code) {
        String aMessage =
            (message != null && !message.trim().isEmpty()) ? message
                : (status.getReasonPhrase() != null ? status.getReasonPhrase() : status.name());
        String aCode = (code != null) ? code : String.valueOf(status.getStatusCode());
        try {
            return JsonHandler.writeToInpustream(new VitamError(aCode)
                .setHttpCode(status.getStatusCode()).setContext(ACCESS_EXTERNAL_MODULE)
                .setState(CODE_VITAM).setMessage(status.getReasonPhrase()).setDescription(aMessage));
        } catch (InvalidParseOperationException e) {
            return new ByteArrayInputStream("{ 'message' : 'Invalid VitamError message' }".getBytes());
        }
    }

    private VitamError getErrorEntity(Status status, String message) {
        String aMessage =
            (message != null && !message.trim().isEmpty()) ? message
                : (status.getReasonPhrase() != null ? status.getReasonPhrase() : status.name());

        return new VitamError(status.name()).setHttpCode(status.getStatusCode()).setContext(ACCESS_EXTERNAL_MODULE)
            .setState(CODE_VITAM).setMessage(status.getReasonPhrase()).setDescription(aMessage);
    }
}
