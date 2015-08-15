/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.stratos.messaging.event.health.stat;


import org.apache.stratos.messaging.event.Event;

public class CurveFinderOfRequestsInFlightEvent extends Event {
    private final String clusterId;
    private final String clusterInstanceId;
    private final String networkPartitionId;
    private final double value;
    /**
     * Curve finder co-efficients
     */
    private final float a;
    private final float b;
    private final float c;

    public CurveFinderOfRequestsInFlightEvent(String clusterId, String clusterInstanceId, String networkPartitionId, double value, float a, float b, float c) {
        this.clusterId = clusterId;
        this.clusterInstanceId = clusterInstanceId;
        this.networkPartitionId = networkPartitionId;
        this.value = value;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public String getClusterId() {
        return clusterId;
    }

    public String getNetworkPartitionId() {
        return networkPartitionId;
    }

    public String getClusterInstanceId() {
        return clusterInstanceId;
    }

    public double getValue() {
        return value;
    }

    public float getA() {
        return a;
    }

    public float getB() {
        return b;
    }

    public float getC() {
        return c;
    }
}
