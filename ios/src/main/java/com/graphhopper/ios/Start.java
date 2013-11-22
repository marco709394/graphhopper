/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.ios;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Peter Karich
 */
public class Start
{

    public static void main( String[] strs ) throws Exception
    {        
        CmdArgs args = CmdArgs.read(strs);
        final GraphHopper hopper = new GraphHopper().init(args);
        String location = args.get("graph.location", null);
        if (location == null)
        {
            System.out.println("location is null?");
            return;
        }

        if (!hopper.load(location))
        {
            System.out.println("cannot open " + location);
            return;
        }

        GraphStorage storage = ((GraphStorage) hopper.getGraph());
        System.out.println("graph:" + storage.getNodes() + " " + storage.getDirectory().getDefaultType());
        final AtomicLong c = new AtomicLong(0);
        new XFirstSearch()
        {
            @Override
            protected boolean goFurther( int nodeId )
            {
                c.incrementAndGet();
                return true;
            }
        }.start(storage.createEdgeExplorer(), 0, true);
        System.out.println("explored: " + c.get() + " nodes");
        final Random rand = new Random(0);

        int count = 200;
        final Graph g = hopper.getGraph();
        final AtomicLong distSum = new AtomicLong(0);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final int maxNode = g.getNodes();
        final String vehicle = "CAR";
        MiniPerfTest miniPerf = new MiniPerfTest()
        {
            @Override
            public int doCalc( boolean warmup, int run )
            {
                int from = rand.nextInt(maxNode);
                int to = rand.nextInt(maxNode);
                double fromLat = g.getLatitude(from);
                double fromLon = g.getLongitude(from);
                double toLat = g.getLatitude(to);
                double toLon = g.getLongitude(to);
                GHResponse res = hopper.route(new GHRequest(fromLat, fromLon, toLat, toLon).
                        setWeighting("fastest").setVehicle(vehicle));
                if (res.hasErrors())
                    throw new IllegalStateException("errors should NOT happen in Measurement! " + res.getErrors());

                if (!warmup)
                {
                    long dist = (long) res.getDistance();
                    if (dist < 1)
                    {
                        failedCount.incrementAndGet();
                        return 0;
                    }

                    distSum.addAndGet(dist);
                }

                return res.getPoints().getSize();
            }
        }.setIterations(count).start();
        System.out.println("report:" + miniPerf.getReport() 
                + ", failed:" + failedCount
                + ", meanDist:" + (float) distSum.get() / count);
        System.out.println("meanTime:" + miniPerf.getMean());
    }
}
