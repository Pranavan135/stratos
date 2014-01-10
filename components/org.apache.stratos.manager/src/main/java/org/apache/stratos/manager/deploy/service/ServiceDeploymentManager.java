/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.manager.deploy.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.deployment.policy.DeploymentPolicy;
import org.apache.stratos.cloud.controller.pojo.CartridgeInfo;
import org.apache.stratos.cloud.controller.pojo.LoadbalancerConfig;
import org.apache.stratos.cloud.controller.pojo.Properties;
import org.apache.stratos.cloud.controller.pojo.Property;
import org.apache.stratos.manager.client.AutoscalerServiceClient;
import org.apache.stratos.manager.client.CloudControllerServiceClient;
import org.apache.stratos.manager.deploy.service.multitenant.MultiTenantService;
import org.apache.stratos.manager.deploy.service.multitenant.lb.MultiTenantLBService;
import org.apache.stratos.manager.exception.ADCException;
import org.apache.stratos.manager.exception.PersistenceManagerException;
import org.apache.stratos.manager.exception.ServiceAlreadyDeployedException;
import org.apache.stratos.manager.exception.UnregisteredCartridgeException;
import org.apache.stratos.manager.retriever.DataInsertionAndRetrievalManager;
import org.apache.stratos.messaging.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class ServiceDeploymentManager {

    private static Log log = LogFactory.getLog(ServiceDeploymentManager.class);
    
    public Service deployService (String type, String autoscalingPolicyName, String deploymentPolicyName, int tenantId, String tenantRange,
    		String tenantDomain, String userName)
            throws ADCException, UnregisteredCartridgeException, ServiceAlreadyDeployedException {

        //check if already we have a Multitenant service deployed for the same type
        DataInsertionAndRetrievalManager dataInsertionAndRetrievalManager = new DataInsertionAndRetrievalManager();

        Service deployedService;
        try {
            deployedService = dataInsertionAndRetrievalManager.getService(type);

        } catch (PersistenceManagerException e) {
            String errorMsg = "Error in checking if Service is available is PersistenceManager";
            log.error(errorMsg, e);
            throw new ADCException(errorMsg, e);
        }

        if (deployedService != null) {
            String errorMsg = "There is an already deployed Service for type " + type;
            log.error(errorMsg);
            throw new ServiceAlreadyDeployedException(errorMsg, type);
        }

        //get deployed Cartridge Definition information
        CartridgeInfo cartridgeInfo;
        try {
            cartridgeInfo = CloudControllerServiceClient.getServiceClient().getCartridgeInfo(type);

        } catch (UnregisteredCartridgeException e) {
            String message = type + " is not a valid cartridgeSubscription type. Please try again with a valid cartridgeSubscription type.";
            log.error(message);
            throw e;

        } catch (Exception e) {
            String message = "Error getting info for " + type;
            log.error(message, e);
            throw new ADCException(message, e);
        }

        if (!cartridgeInfo.getMultiTenant()) {
            String errorMsg = "Cartridge definition with type " + type + " is not multitenant";
            log.error(errorMsg);
            throw new ADCException(errorMsg);
        }

        
        // TODO - LB cartridge.... ??

        List<Property> lbRefProp = new ArrayList<Property>();

        // get lb config reference
        LoadbalancerConfig lbConfig = cartridgeInfo.getLbConfig();

        if (lbConfig == null || lbConfig.getProperties() == null) {
            if (log.isDebugEnabled()) {
                log.debug("This Service does not require a load balancer. " + "[Service Name] " +
                          type);
            }
        } else {

            Service lbService;

            CartridgeInfo lbCartridgeInfo;
            String lbCartridgeType = lbConfig.getType();
            try {
                // retrieve lb Cartridge info
                lbCartridgeInfo = CloudControllerServiceClient.getServiceClient().getCartridgeInfo(lbCartridgeType);
            } catch (Exception e) {
                String msg = "Cannot get cartridge info: " + type;
                log.error(msg, e);
                throw new ADCException(msg, e);
            }

            Properties lbReferenceProperties = lbConfig.getProperties();

            Property property = new Property();
            property.setName(org.apache.stratos.messaging.util.Constants.LOAD_BALANCER_REF);

            for (org.apache.stratos.cloud.controller.pojo.Property prop : lbReferenceProperties.getProperties()) {

                String name = prop.getName();
                String value = prop.getValue();

                // TODO make following a chain of responsibility pattern
                if (Constants.NO_LOAD_BALANCER.equals(name)) {
                    if ("true".equals(value)) {
                        if (log.isDebugEnabled()) {
                            log.debug("This cartridge does not require a load balancer. " +
                                      "[Type] " + type);
                        }
                        property.setValue(name);
                        lbRefProp.add(property);
                        break;
                    }
                } else if (Constants.EXISTING_LOAD_BALANCERS.equals(name)) {
                    String clusterIdsVal = value;
                    if (log.isDebugEnabled()) {
                        log.debug("This cartridge refers to existing load balancers. " + "[Type] " +
                                  type + "[Referenced Cluster Ids] " + clusterIdsVal);
                    }

                    String[] clusterIds = clusterIdsVal.split(",");

                    for (String clusterId : clusterIds) {
                        
                            try {
                            	AutoscalerServiceClient.getServiceClient().checkLBExistenceAgainstPolicy(clusterId,
                            			deploymentPolicyName);
                            } catch (Exception ex) {
                                // we don't need to throw the error here.
                                log.error(ex.getMessage(), ex);
                            }
                        
                    }

                    property.setValue(name);
                    lbRefProp.add(property);
                    break;

                } else if (Constants.DEFAULT_LOAD_BALANCER.equals(name)) {
                    if ("true".equals(value)) {
                        property.setValue(name);
                        if (log.isDebugEnabled()) {
                            log.debug("This cartridge uses default load balancer. " + "[Type] " +
                                      type);
                        }
                        
                            try {
                                // get the valid policies for lb cartridge
                                DeploymentPolicy[] lbCartridgeDepPolicies =
                                	AutoscalerServiceClient.getServiceClient().getDeploymentPolicies(lbCartridgeType);
                                // traverse deployment policies of lb cartridge
                                for (DeploymentPolicy policy : lbCartridgeDepPolicies) {
                                    // check existence of the subscribed policy
                                    if (deploymentPolicyName.equals(policy.getId())) {

                                        if (!AutoscalerServiceClient.getServiceClient().checkDefaultLBExistenceAgainstPolicy(deploymentPolicyName)) {

                                            // if lb cluster doesn't exist
                                            lbCartridgeInfo.addProperties(property);
                                            /*subscribeToLb(lbCartridgeType,
                                                          lbAlias,
                                                          lbCartridgeInfo.getDefaultAutoscalingPolicy(),
                                                          deploymentPolicyName, tenantId,
                                                          userName,
                                                          tenantDomain,
                                                          lbCartridgeInfo.getProperties());*/
                                            lbService = new MultiTenantLBService(lbCartridgeType,
                                                    lbCartridgeInfo.getDefaultAutoscalingPolicy(),
                                                    deploymentPolicyName, tenantId,
                                                    lbCartridgeInfo,
                                                    tenantRange);
                                            lbService.deploy();
                                        }
                                    }
                                }

                            } catch (Exception ex) {
                                // we don't need to throw the error here.
                                log.error(ex.getMessage(), ex);
                            }
                        

                        lbRefProp.add(property);
                        break;
                    } else if (Constants.SERVICE_AWARE_LOAD_BALANCER.equals(name)) {
                        if ("true".equals(value)) {
                            property.setValue(name);
                            if (log.isDebugEnabled()) {
                                log.debug("This cartridge uses a service aware load balancer. " +
                                          "[Type] " + type);
                            }
                            
                                try {

                                    // get the valid policies for lb cartridge
                                    DeploymentPolicy[] lbCartridgeDepPolicies =
                                    	AutoscalerServiceClient.getServiceClient().getDeploymentPolicies(lbCartridgeType);
                                    // traverse deployment policies of lb cartridge
                                    for (DeploymentPolicy policy : lbCartridgeDepPolicies) {
                                        // check existence of the subscribed policy
                                        if (deploymentPolicyName.equals(policy.getId())) {

                                            if (!AutoscalerServiceClient.getServiceClient().checkServiceLBExistenceAgainstPolicy(type,
                                                                                                              deploymentPolicyName)) {

                                                lbCartridgeInfo.addProperties(property);
                                                /*subscribeToLb(lbCartridgeType,
                                                              lbAlias,
                                                              lbCartridgeInfo.getDefaultAutoscalingPolicy(),
                                                              deploymentPolicyName,
                                                              tenantId, 
                                                              userName,
                                                              tenantDomain,
                                                              lbCartridgeInfo.getProperties());*/
                                                lbService = new MultiTenantLBService(lbCartridgeType,
                                                        lbCartridgeInfo.getDefaultAutoscalingPolicy(),
                                                        deploymentPolicyName, tenantId,
                                                        lbCartridgeInfo,
                                                        tenantRange);
                                                lbService.deploy();
                                            }
                                        }
                                    }

                                } catch (Exception ex) {
                                    // we don't need to throw the error here.
                                    log.error(ex.getMessage(), ex);
                                }
                           

                            lbRefProp.add(property);
                            break;
                        }
                    }
                }
            }
        }



        
        Service service = new MultiTenantService(type, autoscalingPolicyName, deploymentPolicyName, tenantId, cartridgeInfo, tenantRange);

        /*//generate the cluster ID (domain)for the service
        service.setClusterId(type + "." + cartridgeInfo.getHostName() + ".domain");
        //host name is the hostname defined in cartridge definition
        service.setHostName(cartridgeInfo.getHostName());

        //Create payload
        BasicPayloadData basicPayloadData = CartridgeSubscriptionUtils.createBasicPayload(service);
        //populate
        basicPayloadData.populatePayload();
        PayloadData payloadData = PayloadFactory.getPayloadDataInstance(cartridgeInfo.getProvider(),
                cartridgeInfo.getType(), basicPayloadData);

        // get the payload parameters defined in the cartridge definition file for this cartridge type
        if (cartridgeInfo.getProperties() != null && cartridgeInfo.getProperties().length != 0) {

            for (Property property : cartridgeInfo.getProperties()) {
                // check if a property is related to the payload. Currently this is done by checking if the
                // property name starts with 'payload_parameter.' suffix. If so the payload param name will
                // be taken as the substring from the index of '.' to the end of the property name.
                if (property.getName()
                        .startsWith(CartridgeConstants.CUSTOM_PAYLOAD_PARAM_NAME_PREFIX)) {
                    String payloadParamName = property.getName();
                    payloadData.add(payloadParamName.substring(payloadParamName.indexOf(".") + 1), property.getValue());
                }
            }
        }

        //set PayloadData instance
        service.setPayloadData(payloadData);*/

        //deploy the service
        service.deploy();

        //persist Service
        /*try {
			PersistenceManager.persistService(service);
		} catch (Exception e) {
            String message = "Error getting info for " + type;
            log.error(message, e);
            throw new ADCException(message, e);
        }*/

        try {
            dataInsertionAndRetrievalManager.persistService(service);

        } catch (PersistenceManagerException e) {
            String errorMsg = "Error in persisting Service in PersistenceManager";
            log.error(errorMsg, e);
            throw new ADCException(errorMsg, e);
        }

        return service;
    }

    public void undeployService (String type) throws ADCException {

        DataInsertionAndRetrievalManager dataInsertionAndRetrievalManager = new DataInsertionAndRetrievalManager();

        Service service;
        try {
            service = dataInsertionAndRetrievalManager.getService(type);

        } catch (PersistenceManagerException e) {
            String errorMsg = "Error in checking if Service is available is PersistenceManager";
            log.error(errorMsg, e);
            throw new ADCException(errorMsg, e);
        }

        if (service == null) {
            String errorMsg = "No service found for type " + type;
            log.error(errorMsg);
            throw new ADCException(errorMsg);
        }

        // if service is found, undeploy
        service.undeploy();

        try {
            dataInsertionAndRetrievalManager.removeService(type);

        } catch (PersistenceManagerException e) {
            String errorMsg = "Error in removing Service from PersistenceManager";
            log.error(errorMsg, e);
            throw new ADCException(errorMsg, e);
        }
    }
    
    /*private void subscribeToLb(String cartridgeType, String lbAlias,
            String defaultAutoscalingPolicy, String deploymentPolicy,
            int tenantId, String userName, String tenantDomain, Property[] props) throws ADCException {
            
            try {
                if(log.isDebugEnabled()) {
                    log.debug("Subscribing to a load balancer [cartridge] "+cartridgeType+" [alias] "+lbAlias);
                }
                CartridgeSubscription cartridgeSubscription = 
                        cartridgeSubsciptionManager.subscribeToCartridgeWithProperties(cartridgeType, lbAlias.trim(), defaultAutoscalingPolicy, 
                                                                         deploymentPolicy,
                                                                         tenantDomain, 
                                                                         tenantId,
                                                                         userName, "git", null, false, null, null, props);
                
                cartridgeSubsciptionManager.registerCartridgeSubscription(cartridgeSubscription);
                
                if(log.isDebugEnabled()) {
                    log.debug("Successfully subscribed to a load balancer [cartridge] "+cartridgeType+" [alias] "+lbAlias);
                }
            } catch (Exception e) {
                String msg = "Error while subscribing to load balancer cartridge [type] "+cartridgeType;
                log.error(msg, e);
                throw new ADCException(msg, e);
            }
        }*/
}
