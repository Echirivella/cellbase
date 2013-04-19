package org.bioinfo.cellbase.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bioinfo.cellbase.lib.common.GenericFeature;
import org.bioinfo.cellbase.lib.common.GenericFeatureChunk;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.*;

public class RegulatoryParserTest {
    String USER_HOME = System.getProperty("user.home");
    static int CHUNKSIZE = 2000;
    Gson gson = new Gson();

    @Test
    public void testCreateSQLiteRegulatoryFiles() throws SQLException, IOException, ClassNotFoundException, NoSuchMethodException {
        List<String> GFFColumnNames = Arrays.asList("seqname", "source", "feature", "start", "end", "score", "strand", "frame", "group");
        List<String> GFFColumnTypes = Arrays.asList("TEXT", "TEXT", "TEXT", "INT", "INT", "TEXT", "TEXT", "TEXT", "TEXT");

        Path regulatoryRegionPath = Paths.get(USER_HOME, "cellbase_v3", "hsapiens", "genomic_regulatory_region");

        Path filePath;

        filePath = regulatoryRegionPath.resolve("AnnotatedFeatures.gff.gz");
        RegulatoryParser.createSQLiteRegulatoryFiles(filePath, "annotated_features", GFFColumnNames, GFFColumnTypes, true);

        filePath = regulatoryRegionPath.resolve("MotifFeatures.gff.gz");
        RegulatoryParser.createSQLiteRegulatoryFiles(filePath, "moti_features", GFFColumnNames, GFFColumnTypes, true);

        filePath = regulatoryRegionPath.resolve("RegulatoryFeatures_MultiCell.gff.gz");
        RegulatoryParser.createSQLiteRegulatoryFiles(filePath, "regulatory_features_multicell", GFFColumnNames, GFFColumnTypes, true);



        GFFColumnNames = Arrays.asList("seqname", "source", "feature", "start", "end", "score", "strand", "frame");
        GFFColumnTypes = Arrays.asList("TEXT", "TEXT", "TEXT", "INT", "INT", "TEXT", "TEXT", "TEXT");
        filePath = regulatoryRegionPath.resolve("gff_mirna_uniq.txt");
        RegulatoryParser.createSQLiteRegulatoryFiles(filePath, "mirna_uniq", GFFColumnNames, GFFColumnTypes, false);

    }


    @Test
    public void testParseRegulatory() throws SQLException, IOException, ClassNotFoundException, NoSuchMethodException {

        Path regulatoryRegionPath = Paths.get(USER_HOME, "cellbase_v3", "hsapiens", "genomic_regulatory_region");
        Path outJsonPath = regulatoryRegionPath.resolve("regulatory.json");

        Path annotatedFilePath = regulatoryRegionPath.resolve("AnnotatedFeatures.gff.gz.db");
        Path motiFilePath = regulatoryRegionPath.resolve("MotifFeatures.gff.gz.db");
        Path regulatoryFilePath = regulatoryRegionPath.resolve("RegulatoryFeatures_MultiCell.gff.gz.db");
        Path mirnaFilePath = regulatoryRegionPath.resolve("gff_mirna_uniq.txt.db");

        BufferedWriter bw = Files.newBufferedWriter(outJsonPath, Charset.defaultCharset(), StandardOpenOption.CREATE);

        Set<String> setChr = new HashSet<String>();
        setChr.addAll(RegulatoryParser.queryChromosomes(annotatedFilePath, "annotated_features"));
        setChr.addAll(RegulatoryParser.queryChromosomes(motiFilePath, "moti_features"));
        setChr.addAll(RegulatoryParser.queryChromosomes(regulatoryFilePath, "regulatory_features_multicell"));
        setChr.addAll(RegulatoryParser.queryChromosomes(mirnaFilePath, "mirna_uniq"));
        List<String> chromosomes = new ArrayList<>();
        chromosomes.addAll(setChr);

        Collections.sort(chromosomes, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                if (o1.equals("X")) o1 = "23";
                if (o2.equals("X")) o2 = "23";
                if (o1.equals("Y")) o1 = "24";
                if (o2.equals("Y")) o2 = "24";
                return Integer.parseInt(o1) - Integer.parseInt(o2);
            }
        });

        List<GenericFeature> annotatedGenericFeatures = new ArrayList<>();
        List<GenericFeature> motiGenericFeatures = new ArrayList<>();
        List<GenericFeature> regulatoryGenericFeatures = new ArrayList<>();
        List<GenericFeature> mirnaGenericFeatures = new ArrayList<>();


        Map<Integer, GenericFeatureChunk> genericFeatureChunks = null;
        for (String chromosome : chromosomes) {

            /*Moti*/
            genericFeatureChunks = new HashMap<>();
            motiGenericFeatures = RegulatoryParser.queryChromosomesRegulatoryDB(motiFilePath, "moti_features", chromosome);
            for(GenericFeature genericFeature :motiGenericFeatures){
                int firstChunkId =  getChunkId(genericFeature.getStart());
                int lastChunkId  = getChunkId(genericFeature.getEnd());

                for(int i=firstChunkId; i<=lastChunkId; i++){
                    if(genericFeatureChunks.get(i)==null){
                        int chunkStart = getChunkStart(i);
                        int chunkEnd = getChunkEnd(i);
                        genericFeatureChunks.put(i,new GenericFeatureChunk(chromosome,i,chunkStart,chunkEnd,new ArrayList<GenericFeature>()));
                    }
                    genericFeatureChunks.get(i).getFeatures().add(genericFeature);
                }
            }
            for (Map.Entry<Integer, GenericFeatureChunk> result : genericFeatureChunks.entrySet()) {
                bw.write(gson.toJson(gson.toJson(result.getValue())) + "\n");
            }
            /*********/

            /*Annotated feature*/
            genericFeatureChunks = new HashMap<>();
            annotatedGenericFeatures = RegulatoryParser.queryChromosomesRegulatoryDB(annotatedFilePath, "annotated_features", chromosome);
            for(GenericFeature genericFeature :annotatedGenericFeatures){
                int firstChunkId =  getChunkId(genericFeature.getStart());
                int lastChunkId  = getChunkId(genericFeature.getEnd());

                for(int i=firstChunkId; i<=lastChunkId; i++){
                    if(genericFeatureChunks.get(i)==null){
                        int chunkStart = getChunkStart(i);
                        int chunkEnd = getChunkEnd(i);
                        genericFeatureChunks.put(i,new GenericFeatureChunk(chromosome,i,chunkStart,chunkEnd,new ArrayList<GenericFeature>()));
                    }
                    genericFeatureChunks.get(i).getFeatures().add(genericFeature);
                }
            }
            for (Map.Entry<Integer, GenericFeatureChunk> result : genericFeatureChunks.entrySet()) {
                bw.write(gson.toJson(gson.toJson(result.getValue())) + "\n");
            }
            /*********/

            /*Regulatory feature*/
            genericFeatureChunks = new HashMap<>();
            regulatoryGenericFeatures = RegulatoryParser.queryChromosomesRegulatoryDB(regulatoryFilePath, "regulatory_features_multicell", chromosome);
            for(GenericFeature genericFeature :regulatoryGenericFeatures){
                int firstChunkId =  getChunkId(genericFeature.getStart());
                int lastChunkId  = getChunkId(genericFeature.getEnd());

                for(int i=firstChunkId; i<=lastChunkId; i++){
                    if(genericFeatureChunks.get(i)==null){
                        int chunkStart = getChunkStart(i);
                        int chunkEnd = getChunkEnd(i);
                        genericFeatureChunks.put(i,new GenericFeatureChunk(chromosome,i,chunkStart,chunkEnd,new ArrayList<GenericFeature>()));
                    }
                    genericFeatureChunks.get(i).getFeatures().add(genericFeature);
                }
            }
            for (Map.Entry<Integer, GenericFeatureChunk> result : genericFeatureChunks.entrySet()) {
                bw.write(gson.toJson(gson.toJson(result.getValue())) + "\n");
            }
            /*********/

            /*Mirna feature*/
            genericFeatureChunks = new HashMap<>();
            mirnaGenericFeatures = RegulatoryParser.queryChromosomesRegulatoryDB(mirnaFilePath, "mirna_uniq", chromosome);
            for(GenericFeature genericFeature :mirnaGenericFeatures){
                int firstChunkId =  getChunkId(genericFeature.getStart());
                int lastChunkId  = getChunkId(genericFeature.getEnd());

                for(int i=firstChunkId; i<=lastChunkId; i++){
                    if(genericFeatureChunks.get(i)==null){
                        int chunkStart = getChunkStart(i);
                        int chunkEnd = getChunkEnd(i);
                        genericFeatureChunks.put(i,new GenericFeatureChunk(chromosome,i,chunkStart,chunkEnd,new ArrayList<GenericFeature>()));
                    }
                    genericFeatureChunks.get(i).getFeatures().add(genericFeature);
                }
            }
            for (Map.Entry<Integer, GenericFeatureChunk> result : genericFeatureChunks.entrySet()) {
                bw.write(gson.toJson(gson.toJson(result.getValue())) + "\n");
            }
            /*********/

        }
        bw.close();

    }

    @Test
    public void testMap(){
        Map<Integer, GenericFeatureChunk> genericFeatureChunks = new HashMap<>();
        System.out.println(genericFeatureChunks.get(0));
    }


    private static int getChunkId(int position){
        return position/CHUNKSIZE;
    }
    private static int getChunkStart(int id){
        if(id==0){
            return 1;
        }else{
            return id*CHUNKSIZE;
        }
    }
    private static int getChunkEnd(int id){
        return (id*CHUNKSIZE)+CHUNKSIZE-1;
    }
}
