package com.m3uviewer.app;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import androidx.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final int PICK_FILE=401;
    private final List<Channel> all=new ArrayList<>(), filtered=new ArrayList<>();
    private ChannelAdapter adapter; private EditText search; private Spinner groups;
    private TextView fileName,stats,resultCount,favoritesCount; private String selected="Tutti";

    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);
        search=findViewById(R.id.search); groups=findViewById(R.id.groupSpinner); fileName=findViewById(R.id.fileName);
        stats=findViewById(R.id.stats); resultCount=findViewById(R.id.resultCount); favoritesCount=findViewById(R.id.favoritesCount);
        ListView list=findViewById(R.id.channelList); adapter=new ChannelAdapter(this,filtered); list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id)->show(filtered.get(pos)));
        findViewById(R.id.btnOpen).setOnClickListener(v->openFile());
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){filter();} public void afterTextChanged(android.text.Editable e){}});
        groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){selected=String.valueOf(p.getItemAtPosition(pos));filter();}});
        setupGroups();
    }
    private void openFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK_FILE);}
    @Override protected void onActivityResult(int r,int code,@Nullable Intent d){super.onActivityResult(r,code,d);if(r!=PICK_FILE||code!=RESULT_OK||d==null||d.getData()==null)return;try{
        List<Channel> p=parse(d.getData());all.clear();all.addAll(p);fileName.setText(name(d.getData()));stats.setText(all.size()+" canali caricati • Tocca un canale per i dettagli");setupGroups();filter();Toast.makeText(this,"Playlist caricata",Toast.LENGTH_SHORT).show();
    }catch(Exception e){Toast.makeText(this,"Errore lettura: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private List<Channel> parse(Uri u)throws Exception{List<Channel> out=new ArrayList<>();InputStream in=getContentResolver().openInputStream(u);if(in==null)throw new IOException("file non accessibile");
        BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));String l,n=null,g="Senza gruppo",id="",logo="";
        while((l=br.readLine())!=null){l=l.trim();if(l.isEmpty())continue;if(l.startsWith("#EXTINF")){int c=l.indexOf(',');n=c>=0?l.substring(c+1).trim():"Canale senza nome";g=attr(l,"group-title","Senza gruppo");id=attr(l,"tvg-id","");logo=attr(l,"tvg-logo","");}
        else if(l.startsWith("#EXTGRP:"))g=l.substring(8).trim();else if(!l.startsWith("#")){if(n==null)n=l;out.add(new Channel(n,l,g,id,logo));n=null;g="Senza gruppo";id="";logo="";}}
        br.close();return out;}
    private String attr(String l,String k,String f){Matcher m=Pattern.compile(k+"\\s*=\\s*\\\"([^\\\"]*)\\\"",Pattern.CASE_INSENSITIVE).matcher(l);return m.find()?m.group(1):f;}
    private String name(Uri u){try(Cursor c=getContentResolver().query(u,null,null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)return c.getString(i);}}catch(Exception ignored){}return "Playlist M3U";}
    private void setupGroups(){LinkedHashSet<String>s=new LinkedHashSet<>();s.add("Tutti");for(Channel c:all)s.add(c.group);ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(s)){public View getView(int p,View v,ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(Color.WHITE);t.setPadding(12,0,12,0);return t;}};groups.setAdapter(a);selected="Tutti";groups.setSelection(0);}
    private void filter(){String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);filtered.clear();for(Channel c:all)if(("Tutti".equals(selected)||c.group.equals(selected))&&(q.isEmpty()||c.name.toLowerCase(Locale.ROOT).contains(q)||c.group.toLowerCase(Locale.ROOT).contains(q)||c.id.toLowerCase(Locale.ROOT).contains(q)))filtered.add(c);adapter.notifyDataSetChanged();resultCount.setText(filtered.size()+" risultati");favoritesCount.setText("★ "+favCount()+" preferiti");}
    private boolean fav(String u){return getPreferences(0).getBoolean("fav_"+Integer.toHexString(u.hashCode()),false);}
    private int favCount(){int n=0;for(Channel c:all)if(fav(c.url))n++;return n;}
    private void toggle(Channel c){String k="fav_"+Integer.toHexString(c.url.hashCode());getPreferences(0).edit().putBoolean(k,!fav(c.url)).apply();filter();}
    private void show(Channel c){new android.app.AlertDialog.Builder(this).setTitle(c.name).setMessage("Gruppo: "+c.group+(c.id.isEmpty()?"":"\nID: "+c.id)+(c.logo.isEmpty()?"":"\nLogo: "+c.logo)+"\n\nURL:\n"+c.url)
        .setPositiveButton("Copia URL",(d,w)->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("M3U URL",c.url));Toast.makeText(this,"URL copiato",Toast.LENGTH_SHORT).show();})
        .setNeutralButton("Apri URL",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(c.url)));}catch(Exception e){Toast.makeText(this,"Nessuna app compatibile",Toast.LENGTH_SHORT).show();}})
        .setNegativeButton("Chiudi",null).show();}
    private class ChannelAdapter extends ArrayAdapter<Channel>{ChannelAdapter(Context c,List<Channel>d){super(c,0,d);}public View getView(int p,View v,ViewGroup parent){if(v==null)v=LayoutInflater.from(MainActivity.this).inflate(R.layout.item_channel,parent,false);Channel c=getItem(p);((TextView)v.findViewById(R.id.channelIndex)).setText(""+(p+1));((TextView)v.findViewById(R.id.channelName)).setText(c.name);((TextView)v.findViewById(R.id.channelGroup)).setText(c.group+(c.id.isEmpty()?"":" • "+c.id));TextView f=v.findViewById(R.id.favorite);f.setText(fav(c.url)?"★":"☆");f.setOnClickListener(x->toggle(c));return v;}}
    static class Channel{final String name,url,group,id,logo;Channel(String n,String u,String g,String i,String l){name=n==null||n.isEmpty()?"Canale senza nome":n;url=u;group=g==null||g.isEmpty()?"Senza gruppo":g;id=i==null?"":i;logo=l==null?"":l;}}
}