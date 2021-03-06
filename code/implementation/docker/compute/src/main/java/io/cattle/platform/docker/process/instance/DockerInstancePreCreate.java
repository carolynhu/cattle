package io.cattle.platform.docker.process.instance;

import static io.cattle.platform.core.model.tables.InstanceTable.*;

import io.cattle.platform.core.constants.DockerInstanceConstants;
import io.cattle.platform.core.constants.InstanceConstants;
import io.cattle.platform.core.model.Credential;
import io.cattle.platform.core.model.Instance;
import io.cattle.platform.core.model.Network;
import io.cattle.platform.core.util.SystemLabels;
import io.cattle.platform.engine.handler.HandlerResult;
import io.cattle.platform.engine.handler.ProcessPreListener;
import io.cattle.platform.engine.process.ProcessInstance;
import io.cattle.platform.engine.process.ProcessState;
import io.cattle.platform.network.NetworkService;
import io.cattle.platform.object.ObjectManager;
import io.cattle.platform.object.util.DataAccessor;
import io.cattle.platform.object.util.DataUtils;
import io.cattle.platform.process.common.handler.AbstractObjectProcessLogic;
import io.cattle.platform.storage.ImageCredentialLookup;
import io.cattle.platform.util.exception.UserException;
import io.cattle.platform.util.type.Priority;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

public class DockerInstancePreCreate extends AbstractObjectProcessLogic implements ProcessPreListener, Priority {

    @Inject
    ObjectManager objectManager;
    @Inject
    NetworkService networkService;
    @Inject
    ImageCredentialLookup credLookup;

    @Override
    public String[] getProcessNames() {
        return new String[] { InstanceConstants.PROCESS_CREATE };
    }

    @Override
    public HandlerResult handle(ProcessState state, ProcessInstance process) {
        Instance instance = (Instance)state.getResource();
        if (!InstanceConstants.CONTAINER_LIKE.contains(instance.getKind())) {
            return null;
        }

        Map<Object, Object> data = new HashMap<>();

        if (instance.getRegistryCredentialId() == null) {
            String uuid = (String) DataAccessor.fields(instance).withKey(InstanceConstants.FIELD_IMAGE_UUID).get();
            Credential cred = credLookup.getDefaultCredential(uuid, instance.getAccountId());
            if (cred != null && cred.getId() != null){
                data.put(INSTANCE.REGISTRY_CREDENTIAL_ID, cred.getId());
            }
        } else {
            data.put(INSTANCE.REGISTRY_CREDENTIAL_ID, instance.getRegistryCredentialId());
        }

        String mode = networkService.getNetworkMode(DataUtils.getFields(instance));
        data.put(DockerInstanceConstants.FIELD_NETWORK_MODE, mode);

        Network network = networkService.resolveNetwork(instance.getAccountId(), mode);
        if (network == null && StringUtils.isNotBlank(mode) && !instance.getNativeContainer()) {
            objectProcessManager.scheduleProcessInstance(InstanceConstants.PROCESS_ERROR, instance, null);
            throw new UserException(String.format("Failed to find network for networkMode %s", mode),
                    null, state.getResource());
        }
        if (network != null) {
            data.put(InstanceConstants.FIELD_NETWORK_IDS, Arrays.asList(network.getId()));
        }

        Map<String, Object> labels = DataAccessor.fieldMap(instance, InstanceConstants.FIELD_LABELS);
        Object ip = labels.get(SystemLabels.LABEL_REQUESTED_IP);
        if (ip != null) {
            data.put(InstanceConstants.FIELD_REQUESTED_IP_ADDRESS, ip.toString());
        }

        return new HandlerResult(data);
    }

    @Override
    public int getPriority() {
        return Priority.PRE - 1;
    }

}
