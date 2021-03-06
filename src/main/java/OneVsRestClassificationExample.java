/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//package org.apache.ignite.examples.ml.multiclass;

import dataFiles.MLSandboxDatasets;
import dataFiles.SandboxMLCache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.ml.dataset.feature.extractor.Vectorizer;
import org.apache.ignite.ml.dataset.feature.extractor.impl.DummyVectorizer;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.multiclass.OneVsRestTrainer;
import org.apache.ignite.ml.svm.SVMLinearClassificationModel;
import org.apache.ignite.ml.svm.SVMLinearClassificationTrainer;

import javax.cache.Cache;
import javax.management.Query;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Run One-vs-Rest multi-class classification trainer ({@link OneVsRestTrainer}) parametrized by binary SVM classifier
 * ({@link SVMLinearClassificationTrainer}) over distributed dataset to build two models: one with min-max scaling and
 * one without min-max scaling.
 * <p>
 * Code in this example launches Ignite grid and fills the cache with test data points (preprocessed
 * <a href="https://archive.ics.uci.edu/ml/datasets/Glass+Identification">Glass dataset</a>).</p>
 * <p>
 * After that it trains two One-vs-Rest multi-class models based on the specified data - one model is with min-max
 * scaling and one without min-max scaling.</p>
 * <p>
 * Finally, this example loops over the test set of data points, applies the trained models to predict what cluster does
 * this point belong to, compares prediction to expected outcome (ground truth), and builds
 * <a href="https://en.wikipedia.org/wiki/Confusion_matrix">confusion matrix</a>.</p>
 * <p>
 * You can change the test data used in this example and re-run it to explore this algorithm further.</p> NOTE: the
 * smallest 3rd class could not be classified via linear SVM here.
 */
public class OneVsRestClassificationExample {
    /**
     * Run example.
     */
    public static String Cache_Name = "cacheML";

    public static void main(String[] args) throws IOException {
        System.out.println();
        System.out.println(">>> One-vs-Rest SVM Multi-class classification model over cached dataset usage example started.");
        // Start ignite grid.
        try (Ignite ignite = Ignition.start("E:/stuff/SVM_test/config/default-config.xml")) {
            System.out.println(">>> Ignite grid started.");


//          ?????????????? ?????????????????????? ????????

            ignite.destroyCache(Cache_Name);

//            CacheConfiguration<Integer, Vector> cacheCfg = new CacheConfiguration<>();
//            cacheCfg.setName(cacheName);
//            cacheCfg.setAffinity(new RendezvousAffinityFunction(false, 1024));
//            IgniteCache newCache = ignite.createCache(cacheCfg);


            IgniteCache<Integer, Vector> dataCache = null;
            try {
                long time;
                time = System.currentTimeMillis();
                System.out.print("Start fillCacheWith .. ");
//                dataCache = new SandboxMLCache(ignite).fillCacheWith(MLSandboxDatasets.MNIST_TRAIN_5);
//                dataCache = new SandboxMLCache(ignite).fillCacheWith(MLSandboxDatasets.MNIST_TRAIN_8);
//                dataCache = new SandboxMLCache(ignite).fillCacheWith(MLSandboxDatasets.MNIST_TRAIN_10);
                dataCache = new SandboxMLCache(ignite).fillCacheWith(MLSandboxDatasets.MNIST_TRAIN_12);
//                dataCache = new SandboxMLCache(ignite).fillCacheWith(MLSandboxDatasets.MNIST_TRAIN_15);
                System.out.println("complete, dataCache.size() = " + dataCache.size() + ", time = " + (System.currentTimeMillis() - time) / 1000.0);
                printKeyAllocation(dataCache);

                time = System.currentTimeMillis();
                SVMLinearClassificationTrainer trainer = new SVMLinearClassificationTrainer();

                Vectorizer<Integer, Vector, Integer, Double> vectorizer = new DummyVectorizer<Integer>()
                        .labeled(Vectorizer.LabelCoordinate.FIRST);
                System.out.print("Start mdl trainer.fit .. ");
                SVMLinearClassificationModel mdl = trainer.fit(
                        ignite,
                        dataCache,
                        vectorizer
                );
                System.out.println("complete, time = " + (System.currentTimeMillis() - time) / 1000.0);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                System.out.println("cache name = " + dataCache.getName());
//                if (dataCache != null)
//                    dataCache.destroy();
            }
        } finally {
            System.out.flush();


        }
    }

    private static void printKeyAllocation(IgniteCache<Integer, Vector> dataCache) {
        String name = dataCache.getName();
        System.out.println();
        System.out.println("?????????????????????????? ?????? " + name);
        Affinity affinity = Ignition.ignite().affinity(name);
        Map<String, Integer> data = new HashMap<>();
//        ScanQuery<Integer, Vector> analogueAvailableQuery = new ScanQuery<>();
        try (QueryCursor<Cache.Entry<Integer, Vector>> cursor = dataCache.query(new ScanQuery<>())) {
            for (Cache.Entry<Integer, Vector> entry : cursor) {
                if (data.get(affinity.mapKeyToNode(entry.getKey()).id().toString()) == null) {
                    data.put(affinity.mapKeyToNode(entry.getKey()).id().toString(), 1);
                } else {
                    data.put(affinity.mapKeyToNode(entry.getKey()).id().toString(), data.get(affinity.mapKeyToNode(entry.getKey()).id().toString()) + 1);
                }
//                System.out.println(entry.getKey() + "," + affinity.mapKeyToNode(entry.getKey()).id().toString() );
//                System.out.println("Value = " + entry.getValue().toString());
            }
            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                System.out.println("ID: " + entry.getKey() + " " + entry.getValue());
            }
        }
    }
}
