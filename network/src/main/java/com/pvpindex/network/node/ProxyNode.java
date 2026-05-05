package com.pvpindex.network.node;

/**
 * @deprecated Use {@link NetworkNode} with {@link NodeType#PROXY} instead.
 */
@Deprecated
public final class ProxyNode extends NetworkNode {

    public ProxyNode(String proxyId, String region) {
        super(proxyId, NodeType.PROXY, region);
    }
}
