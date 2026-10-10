package de.tobias30.claims.client;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Background GitHub release check for the optional client mod. */
final class ClientUpdateChecker {
    private static final Pattern TAG=Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Claims-Client-Updates");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String installed;
    ClientUpdateChecker(String installed){this.installed=installed;}
    void start(){TIMER.scheduleAtFixedRate(this::checkSafely,0,48,TimeUnit.HOURS);}
    private void checkSafely(){try{
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.github.com/repos/Tobias30yt/Claims-Client/releases?per_page=100")).timeout(Duration.ofSeconds(15)).header("Accept","application/vnd.github+json").header("User-Agent","Claims-Client/"+installed).GET().build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("GitHub returned HTTP "+response.statusCode());
        Matcher matcher=TAG.matcher(response.body());List<String> tags=new ArrayList<>();while(matcher.find())tags.add(matcher.group(1));if(tags.isEmpty())throw new IllegalStateException("No release tags in GitHub response");
        String latest=tags.get(0);int behind=(int)tags.stream().filter(tag->compare(tag,installed)>0).count();if(behind==0)return;
        String text="Claims Client update available: you are "+behind+" release"+(behind==1?"":"s")+" behind (installed "+installed+", latest "+latest+"). Please download: https://github.com/Tobias30yt/Claims-Client/releases/tag/"+latest;
        System.getLogger("Claims Client").log(System.Logger.Level.WARNING,text);
    }catch(Exception e){System.getLogger("Claims Client").log(System.Logger.Level.WARNING,"Could not check GitHub for Claims Client updates: "+e.getMessage());}}
    private static int compare(String a,String b){List<Integer>x=parts(a),y=parts(b);for(int i=0;i<Math.max(x.size(),y.size());i++){int l=i<x.size()?x.get(i):0,r=i<y.size()?y.get(i):0;if(l!=r)return Integer.compare(l,r);}return 0;}
    private static List<Integer>parts(String value){Matcher matcher=Pattern.compile("\\d+").matcher(value);List<Integer>out=new ArrayList<>();while(matcher.find())try{out.add(Integer.parseInt(matcher.group()));}catch(NumberFormatException ignored){out.add(Integer.MAX_VALUE);}return out;}
}
