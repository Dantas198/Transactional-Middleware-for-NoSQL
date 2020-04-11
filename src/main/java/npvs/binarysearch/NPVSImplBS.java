package npvs.binarysearch;

import npvs.NPVS;
import utils.ByteArrayWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public class NPVSImplBS implements NPVS {
    //TODO comparar o custo com várias versões desta estrutura

    //ArrayList + binarySearch:
    // O(n) para adicionar
    // O(x) para remover -> vai depender do que for utilizado
    // O(log n) para procura (em média)

    // Nota -> treeMap talvez tenha melhores resultados, por causa da inserção
    // Caso numero de procuras seja reduzido o melhor seria alguma espécie de lista ligada
    private Map<ByteArrayWrapper, ArrayList<Version>> versionsByKey;


    public NPVSImplBS() {
        this.versionsByKey = new HashMap<>();
    }

    @Override
    public void update(Map<ByteArrayWrapper, byte[]> writeMap, long ts){
        writeMap.forEach((key,v) -> {
            Version newV = new Version(v, ts);
            if(versionsByKey.containsKey(key))
                // assumindo que chegam por ordem os pedidos de flush
                this.versionsByKey.get(key).add(0, newV);
            else{
                ArrayList<Version> versions = new ArrayList<>();
                versions.add(newV);
                this.versionsByKey.put(key, versions);
            }
        });
        System.out.println(versionsByKey.toString());
    }

    @Override
    public CompletableFuture<byte[]> read(ByteArrayWrapper key, long ts) {
        if(!versionsByKey.containsKey(key)){
            System.out.println("no key");
            return CompletableFuture.completedFuture(null);
        }
        ArrayList<Version> versions = versionsByKey.get(key);
        return CompletableFuture.completedFuture(getSICompliantVersion(versions, ts));
    }

    // TODO Ver caso sério de ao fazer garbage collection vir um pedir um antigo e só existem versões posteriores a esse
    private byte[] getSICompliantVersion(ArrayList<Version> versions, long ts) {
        // só existem versões antigas ou a minha na cabeça do array
        System.out.println(versions.toString());
        System.out.println("arrived ts: "+ ts);
        int size = versions.size();
        if (ts >= versions.get(0).ts)
            return versions.get(0).value;
        if (ts < versions.get(size - 1).ts)
            return null;
        if (ts == versions.get(size - 1).ts)
            return versions.get(size - 1).value;

        int i = 0, j = size, mid=0;
        while (i < j) {
            mid = (i + j) / 2;
            System.out.println("mid:" + mid);
            if (versions.get(mid).ts == ts)
                return versions.get(mid).value;

            if (ts > versions.get(mid).ts) {
                if (mid > 0 && ts < versions.get(mid - 1).ts)
                    return versions.get(mid).value;
                j = mid;
            }
            else{
                if (mid < size -1 && ts > versions.get(mid+1).ts)
                    return versions.get(mid + 1).value;
                i = mid + 1;
            }
        }
        return versions.get(mid).value;
    }
/*
    private Version getClosest(Version v1, Version v2, long ts){
        if(ts - v1.ts >= v2.ts - ts)
            return v2;
        else
            return v1;
    }

 */
}