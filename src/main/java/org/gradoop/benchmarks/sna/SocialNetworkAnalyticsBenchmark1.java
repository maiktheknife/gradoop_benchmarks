/*
 * Copyright © 2014 - 2019 Leipzig University (Database Research Group)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradoop.benchmarks.sna;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.gradoop.benchmarks.AbstractRunner;
import org.gradoop.flink.algorithms.gelly.labelpropagation.GellyLabelPropagation;
import org.gradoop.flink.model.api.functions.TransformationFunction;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.model.impl.operators.aggregation.ApplyAggregation;
import org.gradoop.flink.model.impl.operators.aggregation.functions.count.EdgeCount;
import org.gradoop.flink.model.impl.operators.aggregation.functions.count.VertexCount;
import org.gradoop.flink.model.impl.operators.combination.ReduceCombination;
import org.gradoop.flink.util.FlinkAsciiGraphLoader;
import org.gradoop.flink.util.GradoopFlinkConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The program executes the following workflow:
 *
 * 1) Extract subgraph with:
 *    - vertex predicate: must be of type 'Person'
 *    - edge predicate: must be of type 'knows'
 * 2) Transform vertices and edges to necessary information
 * 3) Compute communities using Gelly label propagation
 * 4) Compute vertex count per community
 * 5) Select communities with a vertex count greater than a given threshold
 * 6) Combine the remaining graphs to a single graph
 * 7) Group the graph using the vertex attributes 'city' and 'gender' and
 *    - count the number of vertices represented by each super vertex
 *    - count the number of edges represented by each super edge
 * 8) Aggregate the grouped graph:
 *    - add the total vertex count as new graph property
 *    - add the total edge count as new graph property
 *
 * The program can be either executed using external data (for benchmarking) or
 * demo data ({@link #main(String[])}).
 */
public class SocialNetworkAnalyticsBenchmark1 extends AbstractRunner {

	private static Random random = new Random();

	private static List<String> cityNames =
			Arrays.asList("Anqiu", "Lisbon", "Kollam", "Ajmer", "Windhoek", "Kuching", "Antón_Lizardo", "Chiang_Mai",
					"Larkana_District", "Tirunelveli", "Hanover", "Mỹ_Tho", "Isfahan", "Nanning", "Helsinki", "Meiktila", "Garoua", "Ma_Liu_Shui",
					"Baharampur", "Cenxi", "Fayetteville", "Tambacounda", "Thanh_Hóa", "Mahishadal", "Haldia", "Chief", "Gia_lam", "Brussels", "Novo_Hamburgo",
					"Colima", "Ústí_nad_Labem", "Iași", "Samarinda", "Khyber_Pakhtunkhwa", "Raichur", "Botou", "Cebu_City", "Fianarantosa",
					"Changning", "Dalian", "Leuven", "Dar_es_Salaam", "Stuttgart", "České_Budějovice", "Makassar", "Liverpool", "Antwerp", "Kakinada",
					"Rewari", "Gardēz", "Shahrekord", "Jiaganj_Azimganj", "Tianjin", "Copenhagen", "Kathmandu", "Batalanda", "Mysore", "Yongin", "Thane",
					"Moscow_International_Business_Center", "Deyang", "Major_Cities", "New_York_City", "Montemorelos",
					"Dali", "Owariasahi", "Bole",  "Benxi", "Sohag", "Faisalabad", "Raniganj", "Dublin", "Petrópolis", "Luanda", "Posadas",
					"Corrientes", "Beiliu", "Yinchuan", "Montreal", "Erenhot", "Anqing", "Kananga", "Ma'anshan", "Athens", "Noida", "Khairpur", "Prague", "Raipur",
					"Bogotá", "Kitwe", "Jimbaran", "Bahria_Town", "Yongkang_District", "Kadıköy", "Batna", "Islamabad", "Manipal", "Kolda", "Chaozhou", "Jimma",
					"Manaus", "Serro", "El_Oued", "Friedberg", "Quảng_Ngãi", "Melbourne", "Coventry", "Bloemfontein", "Tezpur", "Ratmalana_Airport", "Jamshedpur",
					"Chuzhou", "Medellín",  "Tuyên_Quang", "Kashipur", "Bhagalpur", "Nagasaki", "Puebla", "Richmond", "Qom", "Thrissur",
					"New_York", "Niterói", "Chicago",  "Conghua", "Changge", "Bangkok", "Chibi", "Zinder", "Manchester", "Heidelberg", "Ermita", "Patna",
					"Fuyang,", "Jodhpur",  "Hradec_Králové", "Ilmenau", "Indianapolis", "Bạc_Liêu", "Brisbane", "Unguja", "Flensburg",
					"Hengyang", "Okazaki", "Thirthahalli", "Gao'an", "Kahuta", "Mexico_City", "Gaoping", "Jabalpur", "Zanzibar", "Pondicherry", "Temuco",
					"Kinshasa", "Chaoyang", "Kurukshetra", "Suri", "Al_Mukalla", "Arad", "Midnapore", "Cartagena", "Rohtak", "Magelang", "Bhubaneswar",
					"Harbin", "Krasnodar", "Cincinnati");

  /**
   * Runs the benchmark program.
   *
   * The program can be executed using either external data or demo data.
   *
   * If no arguments are given, the program is executed on a demo social network
   * which is described in 'resources/data/gdl/sna.gdl'. Please note that using demo data prevents the
   * execution on a cluster environment because a local collection format is used to print the
   * graph to the console.
   *
   * For using external data, the following arguments are mandatory:
   *
   * 1) (possibly HDFS) input directory that contains a EPGM graph
   *
   * 2) Format the graph is in (csv, indexed)
   *
   * 3) (possibly HDFS) output directory to write the resulting graph to
   *
   * 4) Threshold for community selection depending on the dataset size:
   *
   * Scale - Threshold (recommended)
   * 1     -     1,000
   * 10    -     7,500
   * 100   -    50,000
   * 1K    -   350,000
   * 10K   - 2,450,000
   *
   * @param args args[0]: input dir, args[1]: input format, args[2]: output dir, args[3]: threshold
   * @throws Exception on failure
   */
  public static void main(String[] args) throws Exception {

    boolean useExternalData = args.length > 0;

    if (useExternalData) {
      executeWithExternalData(args);
    } else {
      executeWithDemoData(GradoopFlinkConfig.createConfig(getExecutionEnvironment()));
    }
  }

  /**
   * Runs the benchmark program with external data (e.g. from HDFS)
   * in a given format (csv, indexed)
   *
   * @param args args[0]: input dir, args[1]: input format, args[2]: output dir, args[3]: threshold
   * @throws Exception on failure
   */
  private static void executeWithExternalData(String[] args) throws Exception {
    Preconditions.checkArgument(
      args.length == 4,
      "input dir, input format, output dir and threshold required");
    String inputDir    = args[0];
    String inputFormat = args[1];
    String outputDir   = args[2];
    int threshold      = Integer.parseInt(args[3]);

    LogicalGraph epgmDatabase = readLogicalGraph(inputDir, inputFormat);

    writeLogicalGraph(execute(epgmDatabase, threshold), outputDir);
  }

  /**
   * Runs the benchmark with demo data. Please note that this can not be executed on a cluster environment
   * because a local collection format is used to print the graph to the console.
   *
   * @param gradoopConf gradoop config
   * @throws Exception on failure
   */
  private static void executeWithDemoData(GradoopFlinkConfig gradoopConf) throws Exception {
    FlinkAsciiGraphLoader loader = new FlinkAsciiGraphLoader(gradoopConf);

    String graphDefinition = IOUtils.toString(SocialNetworkAnalyticsBenchmark1.class
      .getResourceAsStream("/data/gdl/sna.gdl"));

    loader.initDatabaseFromString(graphDefinition);

    LogicalGraph inputGraph = loader.getLogicalGraphByVariable("db");

    System.out.println("Input Graph");
    inputGraph.print();

    LogicalGraph outputGraph = execute(inputGraph, 2);

    System.out.println("Output Graph");
    outputGraph.print();
  }

  /**
   * The actual computation.
   *
   * @param socialNetwork social network graph
   * @param threshold     used in community selection predicate
   * @return summarized, aggregated graph
   */
  private static LogicalGraph execute(LogicalGraph socialNetwork, final int threshold) {

    final int maxIterations   = 4;
    final String vertexCount  = "vertexCount";
    final String edgeCount    = "edgeCount";
    final String person       = "person";
    final String knows        = "knows";
    final String city         = "city";
    final String gender       = "gender";
    final String birthday     = "birthday";
    final String label        = "label";

    return socialNetwork
      // 1) extract subgraph
      .subgraph(
        vertex -> vertex.getLabel().toLowerCase().equals(person),
        edge -> edge.getLabel().toLowerCase().equals(knows))
      // project to necessary information
      .transform(
        // keep graph heads
        TransformationFunction.keep(),
        // keep necessary vertex properties
        (current, transformed) -> {
          transformed.setLabel(current.getLabel());
		  transformed.setProperty(city, cityNames.get(random.nextInt(cityNames.size())));
//		  transformed.setProperty(city, current.getPropertyValue(city));
		  transformed.setProperty(gender, current.getPropertyValue(gender));
          transformed.setProperty(label, current.getPropertyValue(birthday));
          return transformed;
        },
        // keep only edge label
        (current, transformed) -> {
          transformed.setLabel(current.getLabel());
          return transformed;
        })
      // 3a) compute communities
      .callForGraph(new GellyLabelPropagation(maxIterations, label))
      // 3b) separate communities
      .splitBy(label)
      // 4) compute vertex count per community
      .apply(new ApplyAggregation<>(new VertexCount(vertexCount)))
      // 5) select graphs with more than minClusterSize vertices
      .select(g -> g.getPropertyValue(vertexCount).getLong() > threshold)
      // 6) reduce filtered graphs to a single graph using combination
      .reduce(new ReduceCombination<>())
      // 7) group that graph by vertex properties
      .groupBy(Lists.newArrayList(city, gender))
      // 8) count vertices and edges of grouped graph
      .aggregate(new VertexCount(vertexCount), new EdgeCount(edgeCount));
  }
}
