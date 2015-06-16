package io.fabric8.kubernetes.api.model;

import java.util.HashMap;
import java.util.Map;

public enum KubernetesKind {

    List(KubernetesList.class),
    ServiceAccount(ServiceAccount.class),
    ServiceAccountList(ServiceAccountList.class),
    Service(Service.class),
    ServiceList(ServiceList.class),
    Pod(Pod.class),
    PodList(PodList.class),
    ReplicationController(ReplicationController.class),
    ReplicationControllerList(ReplicationControllerList.class),
    Namespace(Namespace.class),
    NamespaceList(NamespaceList.class),
    Secret(Secret.class),
    SecretList(SecretList.class),
    Endpoints(Endpoints.class),
    EndpointsList(EndpointsList.class),
    Node(Node.class),
    NodeList(NodeList.class),
    Build(io.fabric8.openshift.api.model.Build.class),
    BuildList(io.fabric8.openshift.api.model.BuildList.class),
    BuildConfig(io.fabric8.openshift.api.model.BuildConfig.class),
    BuildConfigList(io.fabric8.openshift.api.model.BuildConfigList.class),
    DeploymentConfig(io.fabric8.openshift.api.model.DeploymentConfig.class),
    DeploymentConfigList(io.fabric8.openshift.api.model.DeploymentConfigList.class),
    Image(io.fabric8.openshift.api.model.Image.class),
    ImageList(io.fabric8.openshift.api.model.ImageList.class),
    ImageStream(io.fabric8.openshift.api.model.ImageStream.class),
    ImageStreamList(io.fabric8.openshift.api.model.ImageStreamList.class),
    NameTagReference(io.fabric8.openshift.api.model.NamedTagReference.class),
    NameTagEventList(io.fabric8.openshift.api.model.NamedTagEventList.class),
    Route(io.fabric8.openshift.api.model.Route.class),
    RouteList(io.fabric8.openshift.api.model.RouteList.class),
    Template(io.fabric8.openshift.api.model.template.Template.class),
    TemplateList(io.fabric8.openshift.api.model.template.TemplateList.class),
    OAuthClient(io.fabric8.openshift.api.model.OAuthClient.class),
    OAuthClientList(io.fabric8.openshift.api.model.OAuthClientList.class),
    OAuthClientAuthorization(io.fabric8.openshift.api.model.OAuthClientAuthorization.class),
    OAuthClientAuthorizationList(io.fabric8.openshift.api.model.OAuthClientAuthorizationList.class),
    OAuthAuthorizeToken(io.fabric8.openshift.api.model.OAuthAuthorizeToken.class),
    OAuthAuthorizeTokenList(io.fabric8.openshift.api.model.OAuthAuthorizeTokenList.class),
    OAuthAccessToken(io.fabric8.openshift.api.model.OAuthAccessToken.class),
    OAuthAccessTokenList(io.fabric8.openshift.api.model.OAuthAccessTokenList.class);

    private static final Map<String, Class<? extends KubernetesResource>> map = new HashMap<String, Class<? extends KubernetesResource>>();

    static {
        for (KubernetesKind kind : KubernetesKind.values()) {
            map.put(kind.name(), kind.type);
        }
    }

    private final Class<? extends KubernetesResource> type;

    KubernetesKind(Class type) {
        this.type = type;
    }

    public Class getType() {
        return type;
    }

    public static Class<? extends KubernetesResource> getTypeForName(String name) {
        return map.get(name);
    }
}
