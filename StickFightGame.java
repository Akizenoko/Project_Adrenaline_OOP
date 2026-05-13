import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.*;
import javax.swing.*;

// =====================================================================
//  PROJECT ADRENALINE — Demo Build v4
//  Fixes: sliding movement, harder AI, cutscene, sound effects
//  Compile: javac StickFightGame.java   Run: java StickFightGame
// =====================================================================

// ---- Weapons ----
interface Weapon {
    int getBaseDamage(); double getCritChance(); int getRange(); String getName();
}
class Fists implements Weapon {
    public int getBaseDamage(){return 8;} public double getCritChance(){return 0.15;}
    public int getRange(){return 34;}     public String getName(){return "Fists";}
}
class Sword implements Weapon {
    public int getBaseDamage(){return 18;} public double getCritChance(){return 0.30;}
    public int getRange(){return 62;}      public String getName(){return "Sword";}
}

// =====================================================================
//  SOUND ENGINE — synthesised tones, no audio files needed
// =====================================================================
class SoundEngine {
    private static AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);

    static void play(int type) {
        new Thread(() -> {
            try {
                byte[] buf = generate(type);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                if (!AudioSystem.isLineSupported(info)) return;
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(fmt, buf.length);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ignored) {}
        }).start();
    }

    // type: 0=punch 1=swoosh 2=heavy_hit 3=uppercut 4=spinslash
    //       5=player_hurt 6=enemy_hurt 7=jump 8=victory 9=defeat
    //       10=crit 11=menu_select 12=cutscene_thud 13=combo_chain
    private static byte[] generate(int type) {
        int sr = 44100;
        double[][] spec; // {freq, amp, durationMs, wave}  wave: 0=sine 1=square 2=sawtooth 3=noise
        switch (type) {
            case 0: // punch — short thump
                spec = new double[][]{{120,0.6,60,0},{80,0.4,40,3}};
                break;
            case 1: // swoosh
                spec = new double[][]{{400,0.3,80,2},{200,0.2,60,2}};
                break;
            case 2: // heavy hit
                spec = new double[][]{{80,0.8,100,1},{50,0.6,80,3},{200,0.3,50,0}};
                break;
            case 3: // uppercut — rising tone
                spec = new double[][]{{150,0.7,40,0},{300,0.5,50,0},{500,0.3,40,0}};
                break;
            case 4: // spin slash — descending sweep
                spec = new double[][]{{800,0.5,30,2},{400,0.6,40,2},{200,0.5,60,2},{100,0.4,50,3}};
                break;
            case 5: // player hurt
                spec = new double[][]{{300,0.5,40,3},{150,0.4,60,3}};
                break;
            case 6: // enemy hurt
                spec = new double[][]{{250,0.6,50,3},{120,0.4,50,3}};
                break;
            case 7: // jump — boing
                spec = new double[][]{{200,0.4,40,0},{350,0.3,40,0},{500,0.2,30,0}};
                break;
            case 8: // victory — ascending chime
                spec = new double[][]{{523,0.5,120,0},{659,0.5,120,0},{784,0.5,200,0},{1047,0.4,300,0}};
                break;
            case 9: // defeat
                spec = new double[][]{{400,0.5,100,0},{300,0.5,100,0},{200,0.5,150,0},{100,0.4,200,0}};
                break;
            case 10: // crit — sharp crack
                spec = new double[][]{{1000,0.7,30,1},{500,0.5,40,3},{200,0.4,50,0}};
                break;
            case 11: // menu select
                spec = new double[][]{{440,0.3,50,0},{550,0.3,50,0}};
                break;
            case 12: // cutscene thud
                spec = new double[][]{{60,0.9,200,3},{40,0.7,150,3}};
                break;
            case 13: // combo chain tick
                spec = new double[][]{{660,0.4,30,0},{880,0.3,30,0}};
                break;
            default:
                spec = new double[][]{{440,0.3,50,0}};
        }

        // flatten spec into single buffer with each segment sequential
        int totalSamples = 0;
        for (double[] seg : spec) totalSamples += (int)(sr * seg[2] / 1000.0);
        byte[] buf = new byte[totalSamples * 2];
        int pos = 0;
        for (double[] seg : spec) {
            double freq = seg[0], amp = seg[1];
            int nSamples = (int)(sr * seg[2] / 1000.0);
            int waveType = (int)seg[3];
            for (int i = 0; i < nSamples; i++) {
                double t = (double) i / sr;
                double envelope = 1.0 - (double) i / nSamples; // linear decay
                double v = 0;
                double phase = 2 * Math.PI * freq * t;
                switch (waveType) {
                    case 0: v = Math.sin(phase); break;
                    case 1: v = Math.sin(phase) > 0 ? 1.0 : -1.0; break;
                    case 2: v = 2.0 * ((freq * t) % 1.0) - 1.0; break;
                    case 3: v = (Math.random() * 2 - 1); break;
                }
                short sample = (short)(v * amp * envelope * 32767);
                buf[pos++] = (byte)(sample & 0xff);
                buf[pos++] = (byte)((sample >> 8) & 0xff);
            }
        }
        return buf;
    }
}

// =====================================================================
//  PARTICLES / EFFECTS
// =====================================================================
class Particle {
    double x,y,vx,vy; int life,maxLife; Color col; float size; int type;
    Particle(double x,double y,double vx,double vy,int life,Color col,float sz,int type){
        this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=this.maxLife=life;this.col=col;this.size=sz;this.type=type;
    }
    boolean dead(){return life<=0;}
    void update(){x+=vx;y+=vy;if(type!=3)vy+=0.32;else vy-=0.18;vx*=0.87;life--;}
    void draw(Graphics2D g){
        float a=life/(float)maxLife;
        Color c=new Color(col.getRed(),col.getGreen(),col.getBlue(),(int)(255*a));
        g.setColor(c);int s=Math.max(1,(int)(size*a));
        switch(type){
            case 0:g.setStroke(new BasicStroke(1.8f));g.drawLine((int)x,(int)y,(int)(x-vx*3),(int)(y-vy*3));break;
            case 1:g.fillOval((int)x-s/2,(int)y-s/2,s,s);break;
            case 2:g.setStroke(new BasicStroke(size*a+0.5f));g.drawLine((int)x,(int)y,(int)(x-vx*5),(int)(y-vy*5));break;
            case 3:g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,a*0.38f));g.fillOval((int)x-s,(int)y-s,s*2,s*2);g.setComposite(AlphaComposite.SrcOver);break;
            case 4:g.setStroke(new BasicStroke(1.5f));int hs=s/2;g.drawLine((int)x-hs,(int)y,(int)x+hs,(int)y);g.drawLine((int)x,(int)y-hs,(int)x,(int)y+hs);break;
        }
    }
}
class Shockwave {
    double x,y;float radius,maxRadius;int life,maxLife;Color col;float thick;
    Shockwave(double x,double y,float mr,int l,Color c,float t){this.x=x;this.y=y;maxRadius=mr;life=maxLife=l;col=c;thick=t;radius=4;}
    boolean dead(){return life<=0;}
    void update(){radius=maxRadius*(1f-(life/(float)maxLife));life--;}
    void draw(Graphics2D g){float a=life/(float)maxLife;g.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),(int)(220*a)));g.setStroke(new BasicStroke(thick*a));int r=(int)radius;g.drawOval((int)x-r,(int)y-r,r*2,r*2);}
}
class Ghost {
    double x,y;int life=20;boolean fr;Object state;int af,animf;boolean ip;Weapon w;int cs;
    Ghost(double x,double y,boolean fr,Object st,int af,int animf,boolean ip,Weapon w,int cs){this.x=x;this.y=y;this.fr=fr;state=st;this.af=af;this.animf=animf;this.ip=ip;this.w=w;this.cs=cs;}
    boolean dead(){return life<=0;}
    void update(){life--;}
    void draw(Graphics2D g){float a=life/20f*0.28f;Graphics2D g2=(Graphics2D)g.create();g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,a));if(!fr){g2.translate((int)x*2,0);g2.scale(-1,1);}Player.drawStickman(g2,(int)x,(int)y,state,af,animf,w,ip,cs);g2.dispose();}
}
class ImpactFlash {
    double x,y;int life=14,maxLife=14;Color col;float scale;
    ImpactFlash(double x,double y,Color c,float sc){this.x=x;this.y=y;col=c;scale=sc;}
    boolean dead(){return life<=0;}
    void update(){life--;}
    void draw(Graphics2D g){float a=life/(float)maxLife;int n=8;int[]px=new int[n*2],py=new int[n*2];for(int i=0;i<n*2;i++){double ang=i*Math.PI/n;float r=(i%2==0)?scale*a:scale*a*0.4f;px[i]=(int)(x+r*Math.cos(ang));py[i]=(int)(y+r*Math.sin(ang));}g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,a*0.7f));g.setColor(col);g.fillPolygon(px,py,n*2);g.setComposite(AlphaComposite.SrcOver);g.setColor(new Color(255,255,255,(int)(180*a)));g.setStroke(new BasicStroke(1.5f));g.drawPolygon(px,py,n*2);}
}
class DmgNumber {
    double x,y;int value,life=55;boolean crit;String lbl;
    DmgNumber(double x,double y,int v,boolean c,String l){this.x=x;this.y=y;value=v;crit=c;lbl=l;}
    boolean dead(){return life<=0;}
    void update(){y-=1.5+(crit?0.5:0);life--;}
    void draw(Graphics2D g){float a=life/55f;String txt=(lbl!=null)?lbl:(crit?"★"+value:"-"+value);g.setFont(new Font("Arial",Font.BOLD,crit?24:15));g.setColor(new Color(0,0,0,(int)(160*a)));g.drawString(txt,(int)x+2,(int)y+2);g.setColor(crit?new Color(1f,0.95f,0.1f,a):new Color(1f,0.35f,0.35f,a));g.drawString(txt,(int)x,(int)y);}
}
class ScreenFlash {
    int life,maxLife;Color col;
    ScreenFlash(int l,Color c){life=maxLife=l;col=c;}
    boolean dead(){return life<=0;}
    void update(){life--;}
    void draw(Graphics2D g,int w,int h){float a=life/(float)maxLife*0.38f;g.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),(int)(255*a)));g.fillRect(-10,-10,w+20,h+20);}
}

class EffectManager {
    List<Particle> particles=new ArrayList<>();
    List<Shockwave> shockwaves=new ArrayList<>();
    List<Ghost> ghosts=new ArrayList<>();
    List<DmgNumber> dmgNums=new ArrayList<>();
    List<ImpactFlash> flashes=new ArrayList<>();
    List<ScreenFlash> screens=new ArrayList<>();

    private static double r(){return Math.random();}

    void update(){
        particles.removeIf(p->{p.update();return p.dead();});
        shockwaves.removeIf(s->{s.update();return s.dead();});
        ghosts.removeIf(g->{g.update();return g.dead();});
        dmgNums.removeIf(d->{d.update();return d.dead();});
        flashes.removeIf(f->{f.update();return f.dead();});
        screens.removeIf(s->{s.update();return s.dead();});
    }
    void draw(Graphics2D g,int W,int H){
        for(Ghost gh:ghosts)gh.draw(g);
        for(Particle p:particles)p.draw(g);
        for(Shockwave s:shockwaves)s.draw(g);
        for(ImpactFlash f:flashes)f.draw(g);
        for(DmgNumber d:dmgNums)d.draw(g);
        for(ScreenFlash s:screens)s.draw(g,W,H);
    }
    void hitSparks(double x,double y,boolean fr,Color col){
        int d=fr?1:-1;
        for(int i=0;i<16;i++){double a=r()*Math.PI*2,sp=2.5+r()*5;particles.add(new Particle(x,y,Math.cos(a)*sp+d*2,Math.sin(a)*sp-2.5,14+(int)(r()*10),col,2.5f+(float)r()*2,0));}
        shockwaves.add(new Shockwave(x,y,52,20,col,3f));
        flashes.add(new ImpactFlash(x,y,col,40));
    }
    void bloodSplat(double x,double y,boolean fr){
        Color blood=new Color(180,20,20);
        for(int i=0;i<12;i++){double a=-Math.PI/3+r()*Math.PI*0.66+(fr?0:Math.PI),sp=2+r()*5;particles.add(new Particle(x,y,Math.cos(a)*sp,Math.sin(a)*sp-1.5,22+(int)(r()*14),blood,4f+(float)r()*3,1));}
    }
    void slashTrail(double x,double y,boolean fr,Weapon w){
        int d=fr?1:-1;boolean sw=(w instanceof Sword);Color col=sw?new Color(160,210,255):new Color(255,195,70);
        for(int i=0;i<10;i++){double sp=3.5+r()*4.5;particles.add(new Particle(x+d*9*i,y-i*2.5,sp*d+(r()-0.5),-sp*0.3,12+(int)(r()*9),col,sw?3.8f:2.5f,2));}
    }
    void groundSlam(double x,double y,boolean fr){
        Color col=new Color(220,170,50);int d=fr?1:-1;
        for(int i=0;i<20;i++){double sp=3+r()*6;particles.add(new Particle(x,y-2,sp*d*(0.7+r()*0.6),-(r()*4+1),18+(int)(r()*10),col,3f,0));}
        shockwaves.add(new Shockwave(x,y,110,22,col,2.5f));shockwaves.add(new Shockwave(x,y,60,14,new Color(255,220,100),4f));
    }
    void uppercut(double x,double y){
        Color col=new Color(255,230,60);
        for(int i=0;i<20;i++){double a=r()*Math.PI*2,sp=3.5+r()*5;particles.add(new Particle(x,y,Math.cos(a)*sp,Math.sin(a)*sp-6,18+(int)(r()*10),col,3f+(float)r(),0));}
        for(int i=0;i<8;i++)particles.add(new Particle(x+(r()-0.5)*20,y,(r()-0.5)*3,-(5+r()*4),20,new Color(255,180,30),4f,4));
        shockwaves.add(new Shockwave(x,y,80,26,col,4f));
        screens.add(new ScreenFlash(10,new Color(255,240,100)));
    }
    void spinSlash(double x,double y){
        Color col=new Color(200,70,255),col2=new Color(255,100,255);
        for(int i=0;i<28;i++){double a=i*Math.PI*2/28,sp=4+r()*4;particles.add(new Particle(x,y,Math.cos(a)*sp,Math.sin(a)*sp,22+(int)(r()*12),col,3.2f,2));}
        for(int i=0;i<18;i++){double a=r()*Math.PI*2,sp=3+r()*5;particles.add(new Particle(x,y,Math.cos(a)*sp,Math.sin(a)*sp-2,20+(int)(r()*10),new Color(255,180,255),2.5f,4));}
        shockwaves.add(new Shockwave(x,y,100,30,col,5f));shockwaves.add(new Shockwave(x,y,60,22,col2,3f));shockwaves.add(new Shockwave(x,y,30,14,Color.WHITE,2f));
        screens.add(new ScreenFlash(16,new Color(180,50,255)));
    }
    void critBurst(double x,double y){
        screens.add(new ScreenFlash(12,Color.WHITE));
        shockwaves.add(new Shockwave(x,y,90,28,new Color(255,240,80),4.5f));
        for(int i=0;i<24;i++){double a=r()*Math.PI*2,sp=4+r()*7;particles.add(new Particle(x,y,Math.cos(a)*sp,Math.sin(a)*sp-2,20+(int)(r()*14),new Color(255,220,60),4.5f,0));}
    }
    void smoke(double x,double y){
        for(int i=0;i<5;i++)particles.add(new Particle(x+(r()-0.5)*22,y,(r()-0.5)*1.5,-(1+r()),32+(int)(r()*20),new Color(90,90,110),13f+(float)r()*8,3));
    }
    void enemyHit(double x,double y,boolean fr){hitSparks(x,y,fr,new Color(80,150,255));bloodSplat(x,y,fr);}
    void addGhost(double x,double y,boolean fr,Object st,int af,int animf,boolean ip,Weapon w,int cs){ghosts.add(new Ghost(x,y,fr,st,af,animf,ip,w,cs));}
    void addDmg(double x,double y,int v,boolean crit){dmgNums.add(new DmgNumber(x-10+r()*20,y,v,crit,crit?"★"+v:null));}
}

// =====================================================================
//  Abstract Character
// =====================================================================
abstract class Character {
    double x,y,velX=0,velY=0;
    int health,maxHealth;
    final int W=40,H=100;
    int groundY;
    boolean facingRight=true;
    double knockX=0;int knockTimer=0,hurtFlash=0;

    Character(int x,int y,int hp){this.x=x;this.y=y;maxHealth=hp;health=hp;groundY=y;}

    abstract void draw(Graphics2D g);
    abstract void update(List<Character> others,EffectManager fx);

    Rectangle getHitbox(){return new Rectangle((int)x-W/2,(int)y-H,W,H);}
    void takeDamage(int dmg){health=Math.max(0,health-dmg);hurtFlash=16;}
    void applyKnockback(double kx){knockX=kx;knockTimer=14;}
    boolean isAlive(){return health>0;}

    // FIX: velX is NOT accumulated here — caller sets it per frame or zeroes it
    void physicsStep(int L,int R){
        if(knockTimer>0){x+=knockX;knockX*=0.72;knockTimer--;}
        x+=velX;
        y+=velY;velY+=0.65;
        if(y>=groundY){y=groundY;velY=0;}
        x=Math.max(L,Math.min(R,x));
        if(hurtFlash>0)hurtFlash--;
    }

    void drawHealthBar(Graphics2D g,int bw,int bh){
        int bx=(int)x-bw/2,by=(int)y-H-bh-9;
        g.setColor(new Color(40,0,0));g.fillRect(bx,by,bw,bh);
        float p=health/(float)maxHealth;
        g.setColor(p>0.5f?new Color(40,200,60):p>0.25f?new Color(230,180,0):new Color(220,40,40));
        g.fillRect(bx,by,(int)(bw*p),bh);
        g.setColor(new Color(255,255,255,55));g.fillRect(bx,by,(int)(bw*p),bh/2);
        g.setColor(new Color(180,180,180,80));g.setStroke(new BasicStroke(1f));g.drawRect(bx,by,bw,bh);
    }
}

// =====================================================================
//  Player  —  4-hit combo: JAB → CROSS → UPPERCUT → SPIN SLASH
// =====================================================================
class Player extends Character {
    enum State{IDLE,WALK,JUMP,ATTACK}
    static final int[]    DUR   ={13,13,18,28};
    static final int[]    WIN   ={24,24,30,65};
    static final String[] LABEL ={"JAB","CROSS","UPPERCUT","SPIN SLASH"};
    static final int[]    DMULT ={1,1,2,3};
    static final double[] KBMUL ={1.0,1.4,0.0,1.8};

    State state=State.IDLE;
    Weapon weapon=new Fists();
    int comboStep=0,attackFrame=0;
    boolean hitLanded=false,attacking=false;
    int comboWindow=0,comboCount=0,comboTimer=0;
    String critText="";int critTimer=0;
    int ghostTick=0,animTick=0,animFrame=0;
    int panelW;
    boolean nextQueued=false;

    Player(int x,int y,int pw){super(x,y,100);panelW=pw;}
    void setWeapon(Weapon w){weapon=w;}

    void triggerAttack(){
        if(attacking){nextQueued=true;return;}
        if(comboWindow==0)comboStep=0;
        startSwing();
    }
    private void startSwing(){attacking=true;attackFrame=0;hitLanded=false;nextQueued=false;state=State.ATTACK;animFrame=0;animTick=0;ghostTick=0;}

    Rectangle getAttackBox(){
        int r=weapon.getRange()+(comboStep==3?24:0),ah=36+(comboStep==2?24:0);
        int ay=(int)y-H+(comboStep==2?2:14);
        if(comboStep==3)return new Rectangle((int)x-r,(int)y-H+8,r*2,ah);
        return facingRight?new Rectangle((int)x+12,ay,r,ah):new Rectangle((int)x-12-r,ay,r,ah);
    }

    void checkHit(Character target,EffectManager fx){
        if(!hitLanded&&attacking&&attackFrame>=5&&attackFrame<=DUR[comboStep]-2){
            if(getAttackBox().intersects(target.getHitbox())){
                hitLanded=true;
                boolean crit=Math.random()<weapon.getCritChance()*(comboStep==3?2:1);
                int base=weapon.getBaseDamage()*DMULT[comboStep];
                int dmg=crit?base*2:base;
                target.takeDamage(dmg);
                double kb=KBMUL[comboStep]*(facingRight?1:-1)*(8+comboStep*2.5);
                target.applyKnockback(kb);
                if(comboStep==2)target.velY=-13;
                comboCount++;comboTimer=110;
                if(crit){critText="CRITICAL!";critTimer=45;}
                double hx=target.x+(facingRight?-24:24),hy=target.y-target.H*0.5;
                Color hitCol=comboStep==3?new Color(200,80,255):comboStep==2?new Color(255,230,60):comboStep==1?new Color(255,160,50):new Color(255,100,60);
                fx.hitSparks(hx,hy,facingRight,hitCol);
                fx.bloodSplat(hx,hy,facingRight);
                fx.slashTrail(x,hy,facingRight,weapon);
                fx.addDmg(hx,hy-14,dmg,crit);
                if(comboStep==1)fx.groundSlam(hx,target.y,facingRight);
                if(comboStep==2)fx.uppercut(hx,hy);
                if(comboStep==3)fx.spinSlash(x,(int)y-H/2.0);
                if(crit)fx.critBurst(hx,hy);
                // sounds
                SoundEngine.play(comboStep==2?3:comboStep==3?4:comboStep==1?2:0);
                if(crit)SoundEngine.play(10);
                if(comboStep>0)SoundEngine.play(13);
            }
        }
    }

    @Override
    void update(List<Character> others,EffectManager fx){
        if(comboTimer>0)comboTimer--;else comboCount=0;
        if(critTimer>0)critTimer--;
        if(attacking){
            attackFrame++;
            // SLIDE FIX: decay velX aggressively during attack
            velX*=0.7;
            ghostTick++;
            if(ghostTick>=3&&comboStep>=2){ghostTick=0;fx.addGhost(x,y,facingRight,state,attackFrame,animFrame,true,weapon,comboStep);}
            if(comboStep==3&&attackFrame%4==0)fx.smoke(x,y-H/2.0);
            if(attackFrame>=DUR[comboStep]){
                boolean chain=nextQueued&&comboStep<3;
                attacking=false;hitLanded=false;nextQueued=false;
                if(chain){comboStep++;comboWindow=0;startSwing();}
                else{state=State.IDLE;attackFrame=0;comboWindow=0;if(comboStep>=3)comboStep=0;}
            }else{comboWindow=WIN[comboStep];}
        }else{
            if(comboWindow>0){comboWindow--;if(comboWindow==0)comboStep=0;}
        }
        animTick++;
        if(animTick>=(state==State.WALK?5:8)){animTick=0;animFrame=(animFrame+1)%(state==State.WALK?4:1);}
        physicsStep(32,panelW-32);
    }

    @Override
    void draw(Graphics2D g){
        Graphics2D g2=(Graphics2D)g.create();
        if(!facingRight){g2.translate((int)x*2,0);g2.scale(-1,1);}
        if(hurtFlash>0&&hurtFlash%4<2){g2.setColor(new Color(255,80,80,150));g2.fillRect((int)x-W/2-2,(int)y-H-2,W+4,H+4);}
        drawStickman(g2,(int)x,(int)y,state,attackFrame,animFrame,weapon,true,comboStep);
        g2.dispose();
        drawHealthBar(g,48,7);
        int tx=(int)x;
        if(attacking){Color lc=comboStep==3?new Color(220,100,255):comboStep==2?new Color(255,230,60):comboStep==1?new Color(255,170,50):new Color(255,110,60);g.setFont(new Font("Arial",Font.BOLD,14));g.setColor(new Color(0,0,0,120));g.drawString(LABEL[comboStep],tx-28+1,(int)y-H-23+1);g.setColor(lc);g.drawString(LABEL[comboStep],tx-28,(int)y-H-23);}
        if(comboCount>1&&comboTimer>0){g.setFont(new Font("Arial",Font.BOLD,17));g.setColor(new Color(80,170,255));g.drawString("COMBO x"+comboCount,tx-36,(int)y-H-40);}
        if(critTimer>0){g.setFont(new Font("Arial",Font.BOLD,18));g.setColor(new Color(255,215,30));g.drawString(critText,tx-38,(int)y-H-60);}
        g.setFont(new Font("Monospaced",Font.PLAIN,10));g.setColor(new Color(200,200,200,140));g.drawString("["+weapon.getName()+"]",(int)x-18,(int)y-H-11);
        int dotX=(int)x-14,dotY=(int)y-H-74;
        for(int i=0;i<4;i++){boolean lit=i<comboStep||(attacking&&i==comboStep);Color dc=lit?(comboStep==3?new Color(200,80,255):comboStep==2?new Color(255,230,60):new Color(255,140,40)):new Color(45,50,70);g.setColor(dc);g.fillOval(dotX+i*9,dotY,7,7);g.setColor(new Color(200,200,200,70));g.drawOval(dotX+i*9,dotY,7,7);}
        if(comboWindow>0&&!attacking&&comboStep>0){int bw=70;float pct=comboWindow/(float)WIN[comboStep-1];int bxp=(int)x-bw/2,byp=(int)y-H-82;g.setColor(new Color(0,0,0,100));g.fillRect(bxp,byp,bw,5);g.setColor(new Color(255,200,60,(int)(200*pct)));g.fillRect(bxp,byp,(int)(bw*pct),5);}
    }

    static void drawStickman(Graphics2D g,int cx,int cy,Object stateObj,int atkFrame,int animFr,Weapon weapon,boolean isPlayer,int comboStep){
        boolean isAtk=stateObj.toString().contains("ATTACK"),isWalk=stateObj.toString().contains("WALK"),isJump=stateObj.toString().contains("JUMP");
        float legSwing=isWalk?(float)Math.sin(animFr*Math.PI/2)*28:0,armSwing=isWalk?(float)-Math.sin(animFr*Math.PI/2)*20:0;
        Color bodyCol=isPlayer?new Color(210,55,55):new Color(70,90,130),limbCol=isPlayer?new Color(170,30,30):new Color(50,70,110),headCol=isPlayer?new Color(220,70,70):new Color(85,105,145);
        int headY=cy-100,bodyT=headY+24,bodyB=cy-22,midArm=bodyT+18;
        int dur=isPlayer?DUR[Math.max(0,Math.min(3,comboStep))]:22;
        g.setStroke(new BasicStroke(4.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(limbCol);
        if(isJump){g.drawLine(cx,bodyB,cx+20,bodyB+18);g.drawLine(cx,bodyB,cx-20,bodyB+18);}
        else if(isAtk){if(comboStep==2){g.drawLine(cx,bodyB,cx+24,cy-2);g.drawLine(cx,bodyB,cx-14,cy-2);}else if(comboStep==3){double sa=atkFrame*0.30;g.drawLine(cx,bodyB,cx+(int)(26*Math.cos(sa)),cy-2);g.drawLine(cx,bodyB,cx-(int)(26*Math.cos(sa)),cy-2);}else if(comboStep==1){g.drawLine(cx,bodyB,cx+22,cy-2);g.drawLine(cx,bodyB,cx-10,cy-2);}else{g.drawLine(cx,bodyB,cx+16,cy-2);g.drawLine(cx,bodyB,cx-16,cy-2);}}
        else{double lr=Math.toRadians(legSwing);g.drawLine(cx,bodyB,cx+(int)(22*Math.sin(lr)),cy-2);g.drawLine(cx,bodyB,cx-(int)(22*Math.sin(lr)),cy-2);}
        g.setColor(bodyCol);g.setStroke(new BasicStroke(4.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        if(isAtk&&comboStep==2)g.drawLine(cx-3,bodyT,cx+2,bodyB);else if(isAtk&&comboStep==3){double a=atkFrame*0.18;g.drawLine(cx+(int)(5*Math.sin(a)),bodyT,cx-(int)(5*Math.sin(a)),bodyB);}else if(isAtk&&comboStep==1)g.drawLine(cx+2,bodyT,cx-2,bodyB);else g.drawLine(cx,bodyT,cx,bodyB);
        g.setColor(limbCol);g.setStroke(new BasicStroke(4.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        if(isAtk){float ext=Math.min(1f,atkFrame/7f);if(atkFrame>dur/2)ext=1f-(atkFrame-dur/2)*2f/dur;ext=Math.max(0,ext);
            if(comboStep==2){int upX=cx+(int)(6*ext),upY=bodyT-(int)(50*ext);g.drawLine(cx,midArm,upX,upY);g.drawLine(cx,midArm,cx-22,midArm+18);g.setColor(new Color(255,230,60,(int)(220*ext)));g.fillOval(upX-9,upY-9,18,18);}
            else if(comboStep==3){double sa=atkFrame*0.28;int ax1=cx+(int)(42*Math.cos(sa)),ay1=midArm+(int)(16*Math.sin(sa)),ax2=cx-(int)(42*Math.cos(sa)),ay2=midArm-(int)(16*Math.sin(sa));g.drawLine(cx,midArm,ax1,ay1);g.drawLine(cx,midArm,ax2,ay2);if(weapon instanceof Sword){g.setColor(new Color(210,160,255));g.setStroke(new BasicStroke(3.5f));g.drawLine(ax1,ay1,ax1+(int)(24*Math.cos(sa+0.6)),ay1-(int)(9*Math.sin(sa)));g.drawLine(ax2,ay2,ax2-(int)(24*Math.cos(sa+0.6)),ay2+(int)(9*Math.sin(sa)));}}
            else if(comboStep==1){int px=cx+(int)(weapon.getRange()*0.88*ext),py=midArm+(int)(16*ext)-(int)(4*ext);g.drawLine(cx,midArm,px,py);g.drawLine(cx,midArm,cx-20,midArm-8);if(weapon instanceof Sword){g.setColor(new Color(200,220,255));g.setStroke(new BasicStroke(3.5f));int sx=px+(int)(24*ext);g.drawLine(px,py,sx,py-6);g.fillOval(sx-5,py-10,9,9);}else{g.setColor(new Color(240,190,130));g.fillOval(px-8,py-8,16,16);}}
            else{int px=cx+(int)(weapon.getRange()*0.82*ext),py=midArm-(int)(12*ext);g.drawLine(cx,midArm,px,py);g.drawLine(cx,midArm,cx-22,midArm+14);if(weapon instanceof Sword){g.setColor(new Color(200,220,255));g.setStroke(new BasicStroke(3.5f));int sx=px+(int)(22*ext);g.drawLine(px,py,sx,py-8);g.fillOval(sx-4,py-12,9,9);}else{g.setColor(new Color(235,190,130));g.fillOval(px-8,py-8,16,16);}}}
        else{double ar=Math.toRadians(armSwing);int ax1=cx+(int)(26*Math.sin(ar)),ay1=midArm+18+(int)(6*Math.cos(ar)),ax2=cx-(int)(26*Math.sin(ar));g.drawLine(cx,midArm,ax1,ay1);g.drawLine(cx,midArm,ax2,ay1);if(weapon instanceof Sword){g.setColor(new Color(190,210,240,180));g.setStroke(new BasicStroke(3f));g.drawLine(ax1,ay1,ax1+20,ay1-9);}}
        g.setColor(headCol);g.setStroke(new BasicStroke(2f));int hox=(isAtk&&comboStep==2)?(int)(5*Math.min(1f,atkFrame/8f)):0;
        g.fillOval(cx-13+hox,headY,26,26);g.setColor(isPlayer?Color.BLACK:new Color(200,50,50));g.fillOval(cx-6+hox,headY+7,5,5);g.fillOval(cx+1+hox,headY+7,5,5);
        if(!isPlayer){g.setColor(new Color(180,30,30));g.setStroke(new BasicStroke(2f));g.drawLine(cx-8+hox,headY+4,cx-3+hox,headY+7);g.drawLine(cx+8+hox,headY+4,cx+3+hox,headY+7);}
        else{g.setColor(Color.WHITE);g.fillOval(cx-5+hox,headY+8,2,2);g.fillOval(cx+2+hox,headY+8,2,2);}
        if(isPlayer){g.setColor(new Color(180,30,30,185));int[]sx={cx,cx-6,cx-15,cx-4},sy={bodyT,bodyT+12,bodyT+28,bodyT+9};g.fillPolygon(sx,sy,4);}
    }
    // overload for enemy
    static void drawStickman(Graphics2D g,int cx,int cy,Object st,int af,int animf,Weapon w,boolean ip){drawStickman(g,cx,cy,st,af,animf,w,ip,0);}

}

// =====================================================================
//  Enemy AI — harder: faster, more aggressive, phase 2 at 40% HP
// =====================================================================
class Enemy extends Character {
    enum State{IDLE,WALK,JUMP,ATTACK,DASH}
    State state=State.IDLE;
    int attackFrame=0,attackCooldown=0;
    boolean hitLanded=false;
    int animTick=0,animFrame=0,thinkTimer=25,jumpCooldown=0;
    int panelW;
    boolean phase2=false; // becomes true under 40% HP — faster, more damage
    int dashCooldown=0;

    Enemy(int x,int y,int pw){super(x,y,120);panelW=pw;facingRight=false;}  // 120 HP (was 80)

    Rectangle getAttackBox(){
        int r=phase2?44:36,ah=34,ay=(int)y-H+15;
        return facingRight?new Rectangle((int)x+14,ay,r,ah):new Rectangle((int)x-14-r,ay,r,ah);
    }

    void checkHit(Character target,EffectManager fx){
        if(!hitLanded&&state==State.ATTACK&&attackFrame>=4&&attackFrame<=15){
            if(getAttackBox().intersects(target.getHitbox())){
                hitLanded=true;
                int dmg=(phase2?14:9)+(int)(Math.random()*7);
                target.takeDamage(dmg);
                double kb=facingRight?9:-9;
                target.applyKnockback(kb);
                double hx=target.x+(facingRight?-22:22),hy=target.y-target.H*0.5;
                fx.enemyHit(hx,hy,facingRight);
                fx.addDmg(hx,hy-12,dmg,false);
                SoundEngine.play(5); // player hurt sound
            }
        }
    }

    @Override
    void update(List<Character> others,EffectManager fx){
        if(attackCooldown>0)attackCooldown--;
        if(jumpCooldown>0)jumpCooldown--;
        if(dashCooldown>0)dashCooldown--;

        Character player=others.stream().filter(c->c instanceof Player).findFirst().orElse(null);
        if(player==null||!isAlive()||!player.isAlive())return;

        // enter phase 2
        if(!phase2&&health<maxHealth*0.4){phase2=true;SoundEngine.play(12);}

        double dx=player.x-x;facingRight=dx>0;double dist=Math.abs(dx);
        double speed=phase2?4.2:2.9;

        if(state==State.ATTACK){
            attackFrame++;velX*=0.78;
            if(attackFrame>=(phase2?18:24)){state=State.IDLE;attackFrame=0;hitLanded=false;attackCooldown=phase2?28:50+(int)(Math.random()*20);}
        } else if(state==State.DASH){
            velX=facingRight?speed*2.2:-(speed*2.2);
            if(dist<=65){state=State.ATTACK;attackFrame=0;hitLanded=false;velX=0;}
            else if(Math.abs(velX)<0.5){state=State.IDLE;}
        } else {
            thinkTimer--;
            if(thinkTimer<=0){
                thinkTimer=phase2?14+(int)(Math.random()*14):22+(int)(Math.random()*20);
                if(dist<=65&&attackCooldown==0){state=State.ATTACK;attackFrame=0;hitLanded=false;}
                else if(dist>65){
                    // phase2: occasionally dash at player
                    if(phase2&&dashCooldown==0&&Math.random()<0.4){state=State.DASH;dashCooldown=90;}
                    else{state=State.WALK;velX=(dx>0?1:-1)*speed;}
                }else{velX=(Math.random()<0.5?1:-1)*1.5;state=State.WALK;}
                // jump over player to get behind them
                if(dist<160&&jumpCooldown==0&&Math.random()<(phase2?0.45:0.28)&&y>=groundY){velY=phase2?-14:-12;state=State.JUMP;jumpCooldown=phase2?55:85;}
            }
            if(state==State.WALK)velX=(dx>0?1:-1)*speed;
            else if(state==State.IDLE)velX*=0.5;
        }
        if(state==State.JUMP){velX=(dx>0?1:-1)*(phase2?4.2:3.4);if(y>=groundY)state=State.IDLE;}
        checkHit(player,fx);

        animTick++;
        if(animTick>=(state==State.WALK||state==State.DASH?5:8)){animTick=0;animFrame=(animFrame+1)%(state==State.WALK||state==State.DASH?4:1);}
        physicsStep(32,panelW-32);
    }

    @Override
    void draw(Graphics2D g){
        Graphics2D g2=(Graphics2D)g.create();
        if(!facingRight){g2.translate((int)x*2,0);g2.scale(-1,1);}
        if(hurtFlash>0&&hurtFlash%4<2){g2.setColor(new Color(255,80,80,150));g2.fillRect((int)x-W/2-2,(int)y-H-2,W+4,H+4);}
        // phase2: red glow outline
        if(phase2){g2.setColor(new Color(255,30,30,80));g2.setStroke(new BasicStroke(6f));g2.drawOval((int)x-22,(int)y-H-4,44,H+8);}
        Player.drawStickman(g2,(int)x,(int)y,state,attackFrame,animFrame,new Fists(){public int getRange(){return 37;}},false);
        g2.dispose();
        drawHealthBar(g,48,7);
        g.setFont(new Font("Monospaced",Font.BOLD,10));
        g.setColor(phase2?new Color(255,60,60,210):new Color(200,80,80,180));
        g.drawString(phase2?"ENEMY [!]":"ENEMY",(int)x-20,(int)y-H-11);
    }
}

// =====================================================================
//  Cutscene — 6 frames, drawn procedurally
// =====================================================================
class Cutscene {
    int frame=0,tick=0;
    static final int FRAME_TICKS=150; // ms per frame ~= ticks at 60fps
    static final int TOTAL_FRAMES=6;
    boolean done=false;

    void update(){
        tick++;
        if(tick>=FRAME_TICKS){tick=0;frame++;if(frame>=TOTAL_FRAMES)done=true;}
    }

    void skip(){done=true;}

    void draw(Graphics2D g,int W,int H,LabBackground lab){
        // black bars (cinematic)
        g.drawImage(lab.get(W,H),0,0,null);
        g.setColor(new Color(0,0,0,200));g.fillRect(0,0,W,H);

        int bar=70;
        // text + stickman per frame
        switch(frame){
            case 0: drawFrame(g,W,H,bar,"SECTOR A7 — CONTAINMENT BREACH",
                "An unknown subject has been detected.",70,240,false,false); break;
            case 1: drawFrame(g,W,H,bar,"ALERT: HOSTILE ENTITY IDENTIFIED",
                "Designation: UNKNOWN.  Threat level: EXTREME.",70,240,false,true); break;
            case 2: drawFrame(g,W,H,bar,"SUBJECT ZERO",
                "Last surviving test subject. Highly volatile.",140,240,true,false); break;
            case 3: drawFrame(g,W,H,bar,"YOU MUST FIGHT",
                "There is no escape from Sector A7.",200,240,true,true); break;
            case 4: drawFrame(g,W,H,bar,"FREEDOM IS NOT A SYMPTOM.",
                "It is a choice.",260,260,false,false); break;
            case 5: drawFrame(g,W,H,bar,"— ROUND 1 —",
                "Press J to attack.  Spam J for combos.",320,260,true,true); break;
        }

        // cinematic bars
        g.setColor(Color.BLACK);g.fillRect(0,0,W,bar);g.fillRect(0,H-bar,W,bar);

        // frame counter
        float prog=(frame*FRAME_TICKS+tick)/(float)(TOTAL_FRAMES*FRAME_TICKS);
        g.setColor(new Color(60,60,80));g.fillRect(W/2-100,H-bar+20,200,4);
        g.setColor(new Color(200,100,30));g.fillRect(W/2-100,H-bar+20,(int)(200*prog),4);

        // skip hint
        g.setFont(new Font("Monospaced",Font.PLAIN,10));
        g.setColor(new Color(140,140,160,180));
        g.drawString("ENTER to skip",W-120,H-bar+35);
    }

    private void drawFrame(Graphics2D g,int W,int H,int bar,String title,String sub,int stkX,int stkY,boolean playerSide,boolean enemySide){
        float fade=Math.min(1f,tick/20f);
        // title
        g.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,34));
        FontMetrics fm=g.getFontMetrics();
        int tx=(W-fm.stringWidth(title))/2;
        g.setColor(new Color(0,0,0,(int)(180*fade)));g.drawString(title,tx+2,H/2-20+2);
        g.setColor(new Color(235,220,190,(int)(255*fade)));g.drawString(title,tx,H/2-20);
        // subtitle
        g.setFont(new Font("Monospaced",Font.PLAIN,13));
        fm=g.getFontMetrics();int sx=(W-fm.stringWidth(sub))/2;
        g.setColor(new Color(160,170,200,(int)(220*fade)));g.drawString(sub,sx,H/2+16);

        // stickmen silhouettes
        drawCutsceneStickman(g,playerSide?100:W-120,H-bar-10,true,fade);
        if(enemySide)drawCutsceneStickman(g,enemySide&&playerSide?W-120:W/2+60,H-bar-10,false,fade);

        // glowing scan line across
        if(tick<60){float sl=(tick/60f)*(H-bar*2)+bar;g.setColor(new Color(100,200,255,40));g.setStroke(new BasicStroke(2f));g.drawLine(0,(int)sl,W,(int)sl);}
    }

    private void drawCutsceneStickman(Graphics2D g,int cx,int cy,boolean isPlayer,float alpha){
        Color col=isPlayer?new Color(210,55,55,(int)(200*alpha)):new Color(70,90,130,(int)(200*alpha));
        Color limb=isPlayer?new Color(170,30,30,(int)(200*alpha)):new Color(50,70,110,(int)(200*alpha));
        int headY=cy-95,bodyT=headY+24,bodyB=cy-22,midArm=bodyT+18;
        g.setStroke(new BasicStroke(3.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(limb);
        g.drawLine(cx,bodyB,cx+16,cy-2);g.drawLine(cx,bodyB,cx-16,cy-2);
        g.setColor(col);g.drawLine(cx,bodyT,cx,bodyB);
        g.setColor(limb);g.drawLine(cx,midArm,cx+22,midArm+18);g.drawLine(cx,midArm,cx-22,midArm+18);
        g.setColor(col);g.fillOval(cx-12,headY,24,24);
        // glow halo for player in cutscene
        if(isPlayer){g.setColor(new Color(210,55,55,(int)(60*alpha)));g.fillOval(cx-20,headY-8,40,40);}
    }
}

// =====================================================================
//  Lab Background (cached)
// =====================================================================
class LabBackground {
    private BufferedImage cache;private int cW,cH;
    BufferedImage get(int w,int h){if(cache==null||cW!=w||cH!=h){cW=w;cH=h;cache=build(w,h);}return cache;}
    private BufferedImage build(int w,int h){
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        int fy=h-100;
        g.setColor(new Color(10,12,22));g.fillRect(0,0,w,fy);
        g.setColor(new Color(18,22,38));for(int px=10;px<w-10;px+=90)g.fillRect(px,20,80,fy-30);
        g.setColor(new Color(30,36,60));g.setStroke(new BasicStroke(1f));for(int px=10;px<w-10;px+=90)g.drawRect(px,20,80,fy-30);
        g.setColor(new Color(40,50,80));g.setStroke(new BasicStroke(8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawLine(0,80,w,80);g.drawLine(0,180,w,180);
        g.setColor(new Color(50,62,95));g.setStroke(new BasicStroke(5f));g.drawLine(0,82,w,82);g.drawLine(0,182,w,182);
        g.setColor(new Color(60,75,110));for(int bx=30;bx<w;bx+=60){g.fillOval(bx-5,76,10,10);g.fillOval(bx-5,176,10,10);}
        drawTank(g,60,120,60,160,new Color(0,200,100,120));drawTank(g,150,110,60,170,new Color(100,0,220,100));
        drawTank(g,w-130,115,60,165,new Color(0,140,220,110));drawTank(g,w-60,120,55,160,new Color(220,80,0,100));
        drawWarn(g,w/2-50,30,100,55);
        g.setFont(new Font("Monospaced",Font.BOLD,14));g.setColor(new Color(60,80,120,160));g.drawString("WING  A7",w/2-40,24);
        g.setFont(new Font("Arial",Font.BOLD,11));g.setColor(new Color(180,30,30,180));g.drawString("FREEDOM IS NOT A SYMPTOM.",20,fy-60);
        g.setFont(new Font("Arial",Font.PLAIN,10));g.setColor(new Color(150,150,30,160));g.drawString("THEY TRIED TO BREAK US.",w-190,fy-70);
        g.setFont(new Font("Arial",Font.BOLD,10));g.setColor(new Color(60,200,80,180));g.drawString("WE EVOLVE.",w-170,fy-55);
        drawMon(g,w-110,fy-110,90,70);drawSys(g,12,fy-120,110,90);
        g.setColor(new Color(25,30,50));int sx=w-100;for(int s=0;s<5;s++)g.fillRect(sx+s*14,fy-10-s*18,14*(5-s),18*(s+1));
        g.setColor(new Color(22,26,42));g.fillRect(0,fy,w,100);g.setStroke(new BasicStroke(0.5f));
        for(int fx2=0;fx2<w;fx2+=50)for(int fy2=fy;fy2<h;fy2+=25){g.setColor(new Color(35,42,65));g.drawRect(fx2,fy2,50,25);}
        g.setStroke(new BasicStroke(4f));for(int cs=0;cs<8;cs++){g.setColor(cs%2==0?new Color(200,160,0,120):new Color(0,0,0,60));g.fillRect(cs*14,fy,14,8);g.fillRect(w-112+cs*14,fy,14,8);}
        g.setFont(new Font("Monospaced",Font.BOLD,28));g.setColor(new Color(40,50,80,140));g.drawString("A7  SECTOR",w/2-90,h-20);
        g.setFont(new Font("Arial",Font.BOLD,9));g.setColor(new Color(180,50,50,200));g.drawString("EXPERIMENTATION",w-128,fy-35);g.drawString("BREEDS EVOLUTION",w-128,fy-22);
        drawExit(g,w/2+150,fy-5,50,20);g.dispose();return img;
    }
    private void drawTank(Graphics2D g,int cx,int y,int tw,int th,Color gl){g.setColor(new Color(gl.getRed(),gl.getGreen(),gl.getBlue(),30));g.fillOval(cx-tw/2-15,y-15,tw+30,th+30);g.setColor(new Color(20,25,45));g.fillRoundRect(cx-tw/2,y,tw,th,10,10);g.setColor(gl);g.setStroke(new BasicStroke(1.5f));g.drawRoundRect(cx-tw/2,y,tw,th,10,10);g.setColor(new Color(gl.getRed(),gl.getGreen(),gl.getBlue(),50));g.fillRoundRect(cx-tw/2+3,y+th/3,tw-6,th*2/3-3,8,8);g.setColor(new Color(gl.getRed(),gl.getGreen(),gl.getBlue(),160));int hx=cx,hy=y+th/3;g.setStroke(new BasicStroke(2f));g.drawOval(hx-6,hy,12,12);g.drawLine(hx,hy+12,hx,hy+28);g.drawLine(hx,hy+16,hx-8,hy+24);g.drawLine(hx,hy+16,hx+8,hy+24);g.drawLine(hx,hy+28,hx-7,hy+42);g.drawLine(hx,hy+28,hx+7,hy+42);g.setColor(new Color(255,255,255,28));g.fillRoundRect(cx-tw/2+4,y+4,8,th-8,4,4);}
    private void drawWarn(Graphics2D g,int x,int y,int w,int h){g.setColor(new Color(200,160,0,100));g.fillRect(x,y,w,h);g.setColor(new Color(220,180,0,160));g.setStroke(new BasicStroke(2f));g.drawRect(x,y,w,h);g.setFont(new Font("Monospaced",Font.BOLD,9));g.setColor(new Color(255,220,0,200));g.drawString("!! HAZARDOUS !!",x+6,y+18);g.drawString("EXPERIMENTAL ZONE",x+2,y+32);g.drawString("AUTH. PERSONNEL ONLY",x+1,y+46);}
    private void drawMon(Graphics2D g,int x,int y,int w,int h){g.setColor(new Color(15,20,35));g.fillRect(x,y,w,h);g.setColor(new Color(0,120,200));g.setStroke(new BasicStroke(1.5f));g.drawRect(x,y,w,h);g.setColor(new Color(0,180,80,200));g.setFont(new Font("Monospaced",Font.PLAIN,8));g.drawString("> SUBJECT DATA",x+4,y+14);g.drawString("> VITALS: OK",x+4,y+24);g.drawString("> PHASE: COMBAT",x+4,y+34);g.drawString("> SECTOR: A7",x+4,y+44);g.setColor(new Color(0,255,100,15));for(int sl=y;sl<y+h;sl+=3)g.drawLine(x,sl,x+w,sl);}
    private void drawSys(Graphics2D g,int x,int y,int w,int h){g.setColor(new Color(10,40,20,200));g.fillRect(x,y,w,h);g.setColor(new Color(0,180,60));g.setStroke(new BasicStroke(1.5f));g.drawRect(x,y,w,h);g.setFont(new Font("Monospaced",Font.BOLD,9));g.setColor(new Color(0,220,80));g.drawString("SYSTEM ONLINE",x+4,y+14);g.setFont(new Font("Monospaced",Font.PLAIN,8));g.drawString("SUBJECTS: 0",x+4,y+26);g.drawString("STATUS: STABLE",x+4,y+36);g.setColor(new Color(0,255,60));g.fillOval(x+6,y+46,7,7);g.setColor(new Color(0,200,60,180));g.setStroke(new BasicStroke(1.2f));int[]ekx={x+4,x+14,x+18,x+24,x+30,x+36,x+46,x+50,x+60,x+70,x+80,x+100};int[]eky={y+70,y+70,y+58,y+78,y+60,y+75,y+75,y+60,y+80,y+65,y+75,y+75};g.drawPolyline(ekx,eky,ekx.length);}
    private void drawExit(Graphics2D g,int x,int y,int w,int h){g.setColor(new Color(0,160,0,180));g.fillRect(x,y,w,h);g.setColor(new Color(0,220,0));g.setStroke(new BasicStroke(1f));g.drawRect(x,y,w,h);g.setFont(new Font("Monospaced",Font.BOLD,10));g.setColor(Color.WHITE);g.drawString("EXIT",x+12,y+13);}
}

// =====================================================================
//  Main Game Panel
// =====================================================================
public class StickFightGame extends JPanel implements ActionListener,KeyListener {

    enum Screen{MENU,OPTIONS,CUTSCENE,PLAYING,RESULT}
    Screen screen=Screen.MENU;

    Player player;Enemy enemy;
    List<Character> chars=new ArrayList<>();
    EffectManager fx=new EffectManager();
    LabBackground lab=new LabBackground();
    Cutscene cutscene=new Cutscene();

    String resultMsg="";int resultTimer=0,screenShake=0;
    boolean menuBlink=true;int blinkTimer=0;
    // FIX: track HELD keys as booleans, set velX each frame or zero it
    boolean left,right,up;
    Timer gameTimer;
    static final int W=800,H=520,FLOOR_Y=420;

    public StickFightGame(){
        setPreferredSize(new Dimension(W,H));
        setBackground(new Color(10,12,22));
        setFocusable(true);addKeyListener(this);
        gameTimer=new Timer(16,this);gameTimer.start();
    }

    void initGame(){
        player=new Player(160,FLOOR_Y,W);
        enemy=new Enemy(600,FLOOR_Y,W);
        chars.clear();chars.add(player);chars.add(enemy);
        fx=new EffectManager();
        resultMsg="";resultTimer=0;screenShake=0;
        left=false;right=false;up=false;
    }

    void startCutscene(){
        cutscene=new Cutscene();
        screen=Screen.CUTSCENE;
        SoundEngine.play(12);
    }

    @Override
    public void actionPerformed(ActionEvent e){
        blinkTimer++;if(blinkTimer>40){menuBlink=!menuBlink;blinkTimer=0;}
        switch(screen){
            case CUTSCENE: cutscene.update(); if(cutscene.done){initGame();screen=Screen.PLAYING;} break;
            case PLAYING:  gameLoop(); break;
            default: break;
        }
        repaint();
    }

    void gameLoop(){
        if(player.isAlive()){
            // FIX: set velX directly based on CURRENT key state, don't accumulate
            if(left&&!player.attacking){
                player.velX=-6.5;
                player.facingRight=false;
                if(player.state==Player.State.IDLE)player.state=Player.State.WALK;
            } else if(right&&!player.attacking){
                player.velX=6.5;
                player.facingRight=true;
                if(player.state==Player.State.IDLE)player.state=Player.State.WALK;
            } else {
                // No key held: STOP immediately (was the slide bug)
                if(!player.attacking){
                    player.velX=0;
                    if(player.state==Player.State.WALK)player.state=Player.State.IDLE;
                }
            }
            if(up&&player.y>=player.groundY&&!player.attacking){
                player.velY=-12.5;player.state=Player.State.JUMP;up=false;
                SoundEngine.play(7);
            }
        }
        int prevEHP=enemy.health,prevPHP=player.health;
        player.checkHit(enemy,fx);
        for(Character c:chars)c.update(chars,fx);
        fx.update();
        int dd=prevEHP-enemy.health;if(dd>0){screenShake=Math.max(screenShake,dd>=20?12:7);SoundEngine.play(6);}
        int dp=prevPHP-player.health;if(dp>0)screenShake=Math.max(screenShake,dp>=14?10:5);
        if(screenShake>0)screenShake--;
        if(resultMsg.isEmpty()){
            if(!enemy.isAlive()){resultMsg="VICTORY!";resultTimer=180;SoundEngine.play(8);}
            else if(!player.isAlive()){resultMsg="DEFEATED...";resultTimer=180;SoundEngine.play(9);}
        }
        if(resultTimer>0){resultTimer--;if(resultTimer==0)screen=Screen.RESULT;}
    }

    @Override
    protected void paintComponent(Graphics g0){
        super.paintComponent(g0);
        Graphics2D g=(Graphics2D)g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        switch(screen){
            case MENU:     drawMenu(g);     break;
            case OPTIONS:  drawOptions(g);  break;
            case CUTSCENE: cutscene.draw(g,W,H,lab); break;
            case PLAYING:  drawGame(g);     break;
            case RESULT:   drawResult(g);   break;
        }
        g.dispose();
    }

    void drawMenu(Graphics2D g){
        g.drawImage(lab.get(W,H),0,0,null);
        g.setColor(new Color(0,0,0,65));for(int yl=0;yl<H;yl+=3)g.drawLine(0,yl,W,yl);
        for(int i=0;i<60;i++){g.setColor(new Color(0,0,0,Math.min(255,(int)(i*1.8))));g.drawRect(i,i,W-i*2,H-i*2);}
        g.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,52));
        g.setColor(new Color(200,120,30,65));g.drawString("Project Adrenaline",60,321);
        g.setColor(new Color(235,220,190));g.drawString("Project Adrenaline",57,318);
        int bx=70,by=345,bw=175,bh=38,gap=12;
        drawMenuBtn(g,"▶  Play",bx,by,bw,bh,Color.WHITE);
        drawMenuBtn(g,"⚙  Options",bx,by+bh+gap,bw,bh,Color.WHITE);
        drawMenuBtn(g,"✕  Exit",bx,by+(bh+gap)*2,bw,bh,new Color(255,160,160));
        if(menuBlink){g.setFont(new Font("Monospaced",Font.BOLD,10));g.setColor(new Color(200,100,30,200));g.drawString("—  DEMO VERSION  —  PROJECT ADRENALINE  —",220,H-12);}
        g.setFont(new Font("Monospaced",Font.PLAIN,10));g.setColor(new Color(120,140,180,180));
        g.drawString("PRESS  P  TO  PLAY  /  O  FOR  OPTIONS  /  ESC  TO  EXIT",140,H-28);
    }
    void drawMenuBtn(Graphics2D g,String label,int x,int y,int w,int h,Color col){
        g.setColor(new Color(200,180,120,35));g.fillRect(x,y,w,h);
        g.setColor(new Color(200,180,120,90));g.setStroke(new BasicStroke(1f));g.drawRect(x,y,w,h);
        g.setFont(new Font("Monospaced",Font.BOLD,16));g.setColor(col);g.drawString(label,x+16,y+h-11);
    }

    void drawOptions(Graphics2D g){
        g.drawImage(lab.get(W,H),0,0,null);g.setColor(new Color(0,0,0,165));g.fillRect(0,0,W,H);
        g.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,38));g.setColor(new Color(220,200,160));g.drawString("Options",55,90);
        g.setColor(new Color(200,100,30));g.setStroke(new BasicStroke(2f));g.drawLine(55,100,420,100);
        g.setFont(new Font("Monospaced",Font.BOLD,13));g.setColor(new Color(180,180,200));g.drawString("CONTROLS",55,132);
        String[]keys={"A  /  D","SPACE","J  (spam!)","K"};
        String[]acts={"Move left / right","Jump","JAB → CROSS → UPPERCUT → SPIN SLASH","Switch weapon (Fists / Sword)"};
        g.setFont(new Font("Monospaced",Font.PLAIN,13));
        for(int i=0;i<keys.length;i++){g.setColor(new Color(230,190,80));g.drawString(keys[i],65,160+i*32);g.setColor(new Color(180,200,220));g.drawString("—  "+acts[i],205,160+i*32);}
        g.setFont(new Font("Monospaced",Font.BOLD,11));
        String[]tips={"Movement stops INSTANTLY when key is released — no sliding.",
            "Spam J rapidly to chain all 4 combo hits.",
            "CROSS: ground shockwave  |  UPPERCUT: launches airborne",
            "SPIN SLASH: 360° purple vortex — highest damage",
            "Enemy enters PHASE 2 below 40% HP — faster & harder.",
            "Critical hits: white screen flash + gold burst."};
        for(int i=0;i<tips.length;i++){g.setColor(i==0?new Color(100,220,100):i<4?new Color(200,160,80):new Color(160,120,200));g.drawString("• "+tips[i],65,308+i*20);}
        drawMenuBtn(g,"←  Back",65,440,140,38,new Color(200,200,220));
        g.setFont(new Font("Monospaced",Font.PLAIN,10));g.setColor(new Color(100,100,140));g.drawString("PRESS  O  TO  RETURN",65,H-12);
    }

    void drawGame(Graphics2D g){
        if(screenShake>0){g.translate((int)((Math.random()-0.5)*screenShake*2),(int)((Math.random()-0.5)*screenShake*2));}
        g.drawImage(lab.get(W,H),0,0,null);
        for(Character c:chars){g.setColor(new Color(0,0,0,90));g.fillOval((int)c.x-22,FLOOR_Y-9,44,13);}
        fx.draw(g,W,H);
        for(Character c:chars)c.draw(g);
        drawHUD(g);
        if(!resultMsg.isEmpty()&&resultTimer>0){
            float alpha=Math.min(1f,(180-resultTimer)/15f);
            g.setColor(new Color(0,0,0,(int)(160*alpha)));g.fillRect(0,185,W,95);
            g.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,60));
            boolean win=resultMsg.startsWith("V");
            g.setColor(new Color(win?0.2f:0.9f,win?0.9f:0.2f,0.2f,alpha));
            FontMetrics fm=g.getFontMetrics();g.drawString(resultMsg,(W-fm.stringWidth(resultMsg))/2,250);
        }
        // enemy phase 2 alert
        if(enemy!=null&&enemy.phase2){
            g.setFont(new Font("Monospaced",Font.BOLD,11));
            g.setColor(new Color(255,60,60,180));
            g.drawString("⚠ ENEMY PHASE 2",W-165,42);
        }
        g.setFont(new Font("Monospaced",Font.BOLD,10));g.setColor(new Color(200,100,30,120));g.drawString("DEMO — PROJECT ADRENALINE",W-215,H-8);
    }

    void drawHUD(Graphics2D g){
        drawBigBar(g,20,12,200,16,player.health,player.maxHealth,"PLAYER",new Color(50,200,80));
        drawBigBar(g,W-220,12,200,16,enemy.health,enemy.maxHealth,"ENEMY",new Color(200,60,60));
        g.setFont(new Font("Monospaced",Font.BOLD,11));g.setColor(new Color(180,200,240,200));
        String wp="[ "+player.weapon.getName().toUpperCase()+" ]  K to switch";
        FontMetrics fm=g.getFontMetrics();g.drawString(wp,(W-fm.stringWidth(wp))/2,24);
        g.setFont(new Font("Monospaced",Font.PLAIN,10));g.setColor(new Color(100,120,160,180));
        g.drawString("A/D move   SPACE jump   J spam=combo",(W-232)/2,H-8);
    }

    void drawBigBar(Graphics2D g,int x,int y,int bw,int bh,int hp,int maxHp,String label,Color fill){
        g.setColor(new Color(0,0,0,140));g.fillRect(x-2,y-2,bw+4,bh+18);
        g.setFont(new Font("Monospaced",Font.BOLD,10));g.setColor(new Color(180,190,210));g.drawString(label,x,y+bh+13);
        g.setColor(new Color(50,0,0));g.fillRect(x,y,bw,bh);
        float pct=hp/(float)maxHp;g.setColor(fill);g.fillRect(x,y,(int)(bw*pct),bh);
        g.setColor(new Color(255,255,255,60));g.fillRect(x,y,(int)(bw*pct),bh/2);
        g.setColor(new Color(200,200,200,80));g.setStroke(new BasicStroke(1f));g.drawRect(x,y,bw,bh);
        g.setFont(new Font("Monospaced",Font.BOLD,10));g.setColor(Color.WHITE);g.drawString(hp+" / "+maxHp,x+bw/2-18,y+bh-2);
    }

    void drawResult(Graphics2D g){
        g.drawImage(lab.get(W,H),0,0,null);g.setColor(new Color(0,0,0,185));g.fillRect(0,0,W,H);
        boolean win=enemy!=null&&!enemy.isAlive();
        g.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,68));g.setColor(win?new Color(60,230,100):new Color(230,60,60));
        String msg=win?"VICTORY!":"DEFEATED...";FontMetrics fm=g.getFontMetrics();
        g.drawString(msg,(W-fm.stringWidth(msg))/2,220);
        if(win&&player!=null){g.setFont(new Font("Monospaced",Font.BOLD,15));g.setColor(new Color(200,190,120));
            String sub="Max combo: "+player.comboCount+"   Subject neutralised.";fm=g.getFontMetrics();g.drawString(sub,(W-fm.stringWidth(sub))/2,265);}
        drawMenuBtn(g,"▶  Play Again",(W-180)/2,320,180,44,Color.WHITE);
        drawMenuBtn(g,"←  Main Menu",(W-180)/2,376,180,44,new Color(200,200,240));
        g.setFont(new Font("Monospaced",Font.PLAIN,10));g.setColor(new Color(100,100,140));
        g.drawString("PRESS  R  TO  RETRY  /  M  FOR  MENU",(W-270)/2,H-12);
    }

    @Override
    public void keyPressed(KeyEvent e){
        int c=e.getKeyCode();
        switch(screen){
            case MENU:
                if(c==KeyEvent.VK_P||c==KeyEvent.VK_ENTER){startCutscene();SoundEngine.play(11);}
                if(c==KeyEvent.VK_O){screen=Screen.OPTIONS;SoundEngine.play(11);}
                if(c==KeyEvent.VK_ESCAPE)System.exit(0); break;
            case OPTIONS:
                if(c==KeyEvent.VK_O||c==KeyEvent.VK_ESCAPE){screen=Screen.MENU;SoundEngine.play(11);} break;
            case CUTSCENE:
                if(c==KeyEvent.VK_ENTER||c==KeyEvent.VK_ESCAPE){cutscene.skip();} break;
            case PLAYING:
                if(c==KeyEvent.VK_A)left=true;
                if(c==KeyEvent.VK_D)right=true;
                if(c==KeyEvent.VK_SPACE)up=true;
                if(c==KeyEvent.VK_J)player.triggerAttack();
                if(c==KeyEvent.VK_K){player.setWeapon(player.weapon instanceof Fists?new Sword():new Fists());SoundEngine.play(11);}
                if(c==KeyEvent.VK_ESCAPE)screen=Screen.MENU; break;
            case RESULT:
                if(c==KeyEvent.VK_R){startCutscene();}
                if(c==KeyEvent.VK_M||c==KeyEvent.VK_ESCAPE)screen=Screen.MENU; break;
        }
    }
    @Override
    public void keyReleased(KeyEvent e){
        int c=e.getKeyCode();
        if(c==KeyEvent.VK_A)left=false;
        if(c==KeyEvent.VK_D)right=false;
        if(c==KeyEvent.VK_SPACE)up=false;
    }
    @Override public void keyTyped(KeyEvent e){}

    {addMouseListener(new MouseAdapter(){
        @Override public void mouseClicked(MouseEvent e){
            int mx=e.getX(),my=e.getY();
            if(screen==Screen.MENU){
                if(hit(mx,my,70,345,175,38)){startCutscene();SoundEngine.play(11);}
                if(hit(mx,my,70,395,175,38)){screen=Screen.OPTIONS;SoundEngine.play(11);}
                if(hit(mx,my,70,445,175,38))System.exit(0);
            }else if(screen==Screen.OPTIONS){
                if(hit(mx,my,65,440,140,38)){screen=Screen.MENU;SoundEngine.play(11);}
            }else if(screen==Screen.RESULT){
                int bxm=(W-180)/2;
                if(hit(mx,my,bxm,320,180,44))startCutscene();
                if(hit(mx,my,bxm,376,180,44))screen=Screen.MENU;
            }
            requestFocusInWindow();
        }
    });}
    boolean hit(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}

    public static void main(String[]args){
        SwingUtilities.invokeLater(()->{
            JFrame frame=new JFrame("Project Adrenaline  [DEMO]");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            StickFightGame game=new StickFightGame();
            frame.add(game);frame.pack();frame.setResizable(false);
            frame.setLocationRelativeTo(null);frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}