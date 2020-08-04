/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package opcua.selfunion;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;


public interface ClientExample {

    default String getEndpointUrl() {
        return "opc.tcp://DESKTOP-5CTK5AA:53530/OPCUA/SimulationServer";
    }

    default Predicate<EndpointDescription> endpointFilter() {
        //只要是 就全部放进来 （不过滤）
        return e -> e.getSecurityMode().equals(MessageSecurityMode.SignAndEncrypt);
    }

    default SecurityPolicy getSecurityPolicy() {
        //return SecurityPolicy.None;
        return SecurityPolicy.Basic128Rsa15;
    }

    default IdentityProvider getIdentityProvider() {
        //return new AnonymousProvider();
        return new UsernameProvider("CXCX","251128856");
    }

    void run(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception;

}