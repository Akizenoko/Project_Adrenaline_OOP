import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

// =====================================================================
//  PROJECT ADRENALINE — FINAL BUILD
//  =====================================================================
//  FEATURES:
//  - 1000 HP 3-phase boss (Phase 1: >66%, Phase 2: >33%, Phase 3: ≤33%)
//  - Full pause menu with Resume, Restart, Load Video, Options, Main Menu
//  - Video cutscene import (folder of PNG/JPG frames)
//  - Combo attack system (JAB → CROSS → UPPERCUT → SPIN SLASH)
//  - Particle effects, screen shake, dynamic audio
//  - Weapon switching (Fists / Sword)
//
//  COMPILE: javac ProjectAdrenaline.java
//  RUN:     java ProjectAdrenaline
// =====================================================================

// =====================================================================
//  WEAPON SYSTEM
// =====================================================================

/** Weapon interface defining combat properties */
interface Weapon {
    int getBaseDamage();      // Base damage dealt
    double getCritChance();   // Critical hit chance (0.0 to 1.0)
    int getRange();           // Attack range in pixels
    String getName();         // Weapon display name
}

/** Default fists weapon - balanced, moderate damage */
class Fists implements Weapon {
    public int getBaseDamage() { return 8; }
    public double getCritChance() { return 0.15; }
    public int getRange() { return 34; }
    public String getName() { return "Fists"; }
}

/** Sword weapon - higher damage, longer range, higher crit chance */
class Sword implements Weapon {
    public int getBaseDamage() { return 18; }
    public double getCritChance() { return 0.30; }
    public int getRange() { return 62; }
    public String getName() { return "Sword"; }
}

// =====================================================================
//  SOUND ENGINE — Procedural Audio Generation
// =====================================================================

/** Generates real-time synthesized sound effects without external files */
class SoundEngine {
    private static final AudioFormat FMT = new AudioFormat(44100, 16, 1, true, false);
    
    /** Play sound effect by type ID */
    static void play(int type) {
        new Thread(() -> {
            try {
                byte[] buf = generateSound(type);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, FMT);
                if (!AudioSystem.isLineSupported(info)) return;
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(FMT, buf.length);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ignored) {}
        }).start();
    }
    
    /** Generate raw PCM audio data for given sound type */
    private static byte[] generateSound(int type) {
        int sampleRate = 44100;
        double[][] segments; // [frequency, amplitude, durationMs, waveformType]
        
        // Define sound patterns for different effects
        switch (type) {
            case 0:  segments = new double[][]{{120, .6, 60, 0}, {80, .4, 40, 3}}; break;           // punch
            case 1:  segments = new double[][]{{400, .3, 80, 2}, {200, .2, 60, 2}}; break;          // swoosh
            case 2:  segments = new double[][]{{80, .8, 100, 1}, {50, .6, 80, 3}, {200, .3, 50, 0}}; break; // heavy hit
            case 3:  segments = new double[][]{{150, .7, 40, 0}, {300, .5, 50, 0}, {500, .3, 40, 0}}; break; // uppercut
            case 4:  segments = new double[][]{{800, .5, 30, 2}, {400, .6, 40, 2}, {200, .5, 60, 2}, {100, .4, 50, 3}}; break; // spin slash
            case 5:  segments = new double[][]{{300, .5, 40, 3}, {150, .4, 60, 3}}; break;          // player hurt
            case 6:  segments = new double[][]{{250, .6, 50, 3}, {120, .4, 50, 3}}; break;          // enemy hurt
            case 7:  segments = new double[][]{{200, .4, 40, 0}, {350, .3, 40, 0}, {500, .2, 30, 0}}; break; // jump
            case 8:  segments = new double[][]{{523, .5, 120, 0}, {659, .5, 120, 0}, {784, .5, 200, 0}, {1047, .4, 300, 0}}; break; // victory
            case 9:  segments = new double[][]{{400, .5, 100, 0}, {300, .5, 100, 0}, {200, .5, 150, 0}, {100, .4, 200, 0}}; break; // defeat
            case 10: segments = new double[][]{{1000, .7, 30, 1}, {500, .5, 40, 3}, {200, .4, 50, 0}}; break; // critical hit
            case 11: segments = new double[][]{{440, .3, 50, 0}, {550, .3, 50, 0}}; break;          // menu select
            case 12: segments = new double[][]{{60, .9, 200, 3}, {40, .7, 150, 3}}; break;          // thud
            case 13: segments = new double[][]{{660, .4, 30, 0}, {880, .3, 30, 0}}; break;          // combo
            case 14: segments = new double[][]{{200, .5, 60, 3}, {150, .6, 80, 3}, {100, .7, 100, 3}}; break; // phase change
            default: segments = new double[][]{{440, .3, 50, 0}};
        }
        
        // Calculate total buffer size
        int totalSamples = 0;
        for (double[] seg : segments) totalSamples += (int)(sampleRate * seg[2] / 1000.0);
        byte[] buffer = new byte[totalSamples * 2];
        int position = 0;
        
        // Generate each audio segment
        for (double[] seg : segments) {
            double freq = seg[0], amplitude = seg[1];
            int numSamples = (int)(sampleRate * seg[2] / 1000.0);
            int waveformType = (int)seg[3];
            
            for (int i = 0; i < numSamples; i++) {
                double t = (double)i / sampleRate;
                double envelope = 1.0 - (double)i / numSamples;  // linear decay envelope
                double phase = 2 * Math.PI * freq * t;
                double value = 0;
                
                // Generate waveform based on type
                switch (waveformType) {
                    case 0: value = Math.sin(phase); break;           // sine wave
                    case 1: value = Math.sin(phase) > 0 ? 1 : -1; break; // square wave
                    case 2: value = 2 * ((freq * t) % 1.0) - 1; break;    // sawtooth
                    case 3: value = Math.random() * 2 - 1; break;    // noise
                }
                
                short sample = (short)(value * amplitude * envelope * 32767);
                buffer[position++] = (byte)(sample & 0xff);
                buffer[position++] = (byte)((sample >> 8) & 0xff);
            }
        }
        return buffer;
    }
}

// =====================================================================
//  VISUAL EFFECTS SYSTEM — Particles, Shockwaves, Ghosts, Damage Numbers
// =====================================================================

/** Individual particle for sparks, blood, smoke effects */
class Particle {
    double x, y, vx, vy;
    int life, maxLife;
    Color color;
    float size;
    int type;  // 0:spark line, 1:circle, 2:trail, 3:smoke, 4:star
    
    Particle(double x, double y, double vx, double vy, int life, Color color, float size, int type) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.life = this.maxLife = life;
        this.color = color; this.size = size; this.type = type;
    }
    
    boolean dead() { return life <= 0; }
    
    void update() {
        x += vx; y += vy;
        if (type != 3) vy += 0.32;      // gravity for most particles
        else vy -= 0.18;                // smoke rises
        vx *= 0.87;
        life--;
    }
    
    void draw(Graphics2D g) {
        float alpha = life / (float)maxLife;
        Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * alpha));
        g.setColor(c);
        int s = Math.max(1, (int)(size * alpha));
        
        switch (type) {
            case 0: // spark line
                g.setStroke(new BasicStroke(1.8f));
                g.drawLine((int)x, (int)y, (int)(x - vx*3), (int)(y - vy*3));
                break;
            case 1: // blood circle
                g.fillOval((int)x - s/2, (int)y - s/2, s, s);
                break;
            case 2: // slash trail
                g.setStroke(new BasicStroke(size * alpha + 0.5f));
                g.drawLine((int)x, (int)y, (int)(x - vx*5), (int)(y - vy*5));
                break;
            case 3: // smoke cloud
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.38f));
                g.fillOval((int)x - s, (int)y - s, s*2, s*2);
                g.setComposite(AlphaComposite.SrcOver);
                break;
            case 4: // star burst
                g.setStroke(new BasicStroke(1.5f));
                int hs = s/2;
                g.drawLine((int)x - hs, (int)y, (int)x + hs, (int)y);
                g.drawLine((int)x, (int)y - hs, (int)x, (int)y + hs);
                break;
        }
    }
}

/** Circular shockwave effect on impact */
class Shockwave {
    double x, y;
    float radius, maxRadius;
    int life, maxLife;
    Color color;
    float thickness;
    
    Shockwave(double x, double y, float maxRadius, int life, Color color, float thickness) {
        this.x = x; this.y = y;
        this.maxRadius = maxRadius;
        this.life = this.maxLife = life;
        this.color = color;
        this.thickness = thickness;
        this.radius = 4;
    }
    
    boolean dead() { return life <= 0; }
    
    void update() {
        radius = maxRadius * (1f - (life / (float)maxLife));
        life--;
    }
    
    void draw(Graphics2D g) {
        float alpha = life / (float)maxLife;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(220 * alpha)));
        g.setStroke(new BasicStroke(thickness * alpha));
        int r = (int)radius;
        g.drawOval((int)x - r, (int)y - r, r*2, r*2);
    }
}

/** Ghost trail for attack animations */
class Ghost {
    double x, y;
    int life = 20;
    boolean facingRight;
    Object state;
    int attackFrame, animFrame;
    boolean isPlayer;
    Weapon weapon;
    int comboStep;
    
    Ghost(double x, double y, boolean facingRight, Object state, int attackFrame, int animFrame, 
          boolean isPlayer, Weapon weapon, int comboStep) {
        this.x = x; this.y = y;
        this.facingRight = facingRight;
        this.state = state;
        this.attackFrame = attackFrame;
        this.animFrame = animFrame;
        this.isPlayer = isPlayer;
        this.weapon = weapon;
        this.comboStep = comboStep;
    }
    
    boolean dead() { return life <= 0; }
    void update() { life--; }
    
    void draw(Graphics2D g) {
        float alpha = life / 20f * 0.28f;
        Graphics2D g2 = (Graphics2D)g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        if (!facingRight) {
            g2.translate((int)x * 2, 0);
            g2.scale(-1, 1);
        }
        Player.drawStickman(g2, (int)x, (int)y, state, attackFrame, animFrame, weapon, isPlayer, comboStep);
        g2.dispose();
    }
}

/** Flash effect on hit impact */
class ImpactFlash {
    double x, y;
    int life = 14, maxLife = 14;
    Color color;
    float scale;
    
    ImpactFlash(double x, double y, Color color, float scale) {
        this.x = x; this.y = y;
        this.color = color;
        this.scale = scale;
    }
    
    boolean dead() { return life <= 0; }
    void update() { life--; }
    
    void draw(Graphics2D g) {
        float alpha = life / (float)maxLife;
        int n = 8;
        int[] px = new int[n*2], py = new int[n*2];
        for (int i = 0; i < n*2; i++) {
            double angle = i * Math.PI / n;
            float r = (i % 2 == 0) ? scale * alpha : scale * alpha * 0.4f;
            px[i] = (int)(x + r * Math.cos(angle));
            py[i] = (int)(y + r * Math.sin(angle));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.7f));
        g.setColor(color);
        g.fillPolygon(px, py, n*2);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(255, 255, 255, (int)(180 * alpha)));
        g.setStroke(new BasicStroke(1.5f));
        g.drawPolygon(px, py, n*2);
    }
}

/** Floating damage numbers */
class DmgNumber {
    double x, y;
    int value, life = 55;
    boolean critical;
    String label;
    
    DmgNumber(double x, double y, int value, boolean critical, String label) {
        this.x = x; this.y = y;
        this.value = value;
        this.critical = critical;
        this.label = label;
    }
    
    boolean dead() { return life <= 0; }
    void update() { y -= 1.5 + (critical ? 0.5 : 0); life--; }
    
    void draw(Graphics2D g) {
        float alpha = life / 55f;
        String text = (label != null) ? label : (critical ? "★" + value : "-" + value);
        g.setFont(new Font("Arial", Font.BOLD, critical ? 24 : 15));
        g.setColor(new Color(0, 0, 0, (int)(160 * alpha)));
        g.drawString(text, (int)x + 2, (int)y + 2);
        g.setColor(critical ? new Color(1f, .95f, .1f, alpha) : new Color(1f, .35f, .35f, alpha));
        g.drawString(text, (int)x, (int)y);
    }
}

/** Full-screen flash effect */
class ScreenFlash {
    int life, maxLife;
    Color color;
    
    ScreenFlash(int life, Color color) {
        this.life = this.maxLife = life;
        this.color = color;
    }
    
    boolean dead() { return life <= 0; }
    void update() { life--; }
    
    void draw(Graphics2D g, int width, int height) {
        float alpha = life / (float)maxLife * 0.38f;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * alpha)));
        g.fillRect(-10, -10, width + 20, height + 20);
    }
}

/** Central manager for all visual effects */
class EffectManager {
    List<Particle> particles = new ArrayList<>();
    List<Shockwave> shockwaves = new ArrayList<>();
    List<Ghost> ghosts = new ArrayList<>();
    List<DmgNumber> damageNumbers = new ArrayList<>();
    List<ImpactFlash> flashes = new ArrayList<>();
    List<ScreenFlash> screenFlashes = new ArrayList<>();
    
    private static double random() { return Math.random(); }
    
    void update() {
        particles.removeIf(p -> { p.update(); return p.dead(); });
        shockwaves.removeIf(s -> { s.update(); return s.dead(); });
        ghosts.removeIf(g -> { g.update(); return g.dead(); });
        damageNumbers.removeIf(d -> { d.update(); return d.dead(); });
        flashes.removeIf(f -> { f.update(); return f.dead(); });
        screenFlashes.removeIf(s -> { s.update(); return s.dead(); });
    }
    
    void draw(Graphics2D g, int width, int height) {
        for (Ghost ghost : ghosts) ghost.draw(g);
        for (Particle p : particles) p.draw(g);
        for (Shockwave s : shockwaves) s.draw(g);
        for (ImpactFlash f : flashes) f.draw(g);
        for (DmgNumber d : damageNumbers) d.draw(g);
        for (ScreenFlash s : screenFlashes) s.draw(g, width, height);
    }
    
    /** Create hit impact effects */
    void hitSparks(double x, double y, boolean facingRight, Color color) {
        int dir = facingRight ? 1 : -1;
        for (int i = 0; i < 16; i++) {
            double angle = random() * Math.PI * 2;
            double speed = 2.5 + random() * 5;
            particles.add(new Particle(x, y, Math.cos(angle) * speed + dir * 2, 
                          Math.sin(angle) * speed - 2.5, 14 + (int)(random() * 10), 
                          color, 2.5f + (float)random() * 2, 0));
        }
        shockwaves.add(new Shockwave(x, y, 52, 20, color, 3f));
        flashes.add(new ImpactFlash(x, y, color, 40));
    }
    
    /** Blood splatter effect */
    void bloodSplat(double x, double y, boolean facingRight) {
        Color blood = new Color(180, 20, 20);
        for (int i = 0; i < 12; i++) {
            double angle = -Math.PI/3 + random() * Math.PI * 0.66 + (facingRight ? 0 : Math.PI);
            double speed = 2 + random() * 5;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed - 1.5,
                          22 + (int)(random() * 14), blood, 4f + (float)random() * 3, 1));
        }
    }
    
    /** Slash trail effect */
    void slashTrail(double x, double y, boolean facingRight, Weapon weapon) {
        int dir = facingRight ? 1 : -1;
        boolean isSword = (weapon instanceof Sword);
        Color color = isSword ? new Color(160, 210, 255) : new Color(255, 195, 70);
        for (int i = 0; i < 10; i++) {
            double speed = 3.5 + random() * 4.5;
            particles.add(new Particle(x + dir * 9 * i, y - i * 2.5,
                          speed * dir + (random() - 0.5), -speed * 0.3,
                          12 + (int)(random() * 9), color, isSword ? 3.8f : 2.5f, 2));
        }
    }
    
    /** Ground slam shockwave */
    void groundSlam(double x, double y, boolean facingRight) {
        Color color = new Color(220, 170, 50);
        int dir = facingRight ? 1 : -1;
        for (int i = 0; i < 20; i++) {
            double speed = 3 + random() * 6;
            particles.add(new Particle(x, y - 2, speed * dir * (0.7 + random() * 0.6),
                          -(random() * 4 + 1), 18 + (int)(random() * 10), color, 3f, 0));
        }
        shockwaves.add(new Shockwave(x, y, 110, 22, color, 2.5f));
        shockwaves.add(new Shockwave(x, y, 60, 14, new Color(255, 220, 100), 4f));
    }
    
    /** Uppercut effect */
    void uppercut(double x, double y) {
        Color color = new Color(255, 230, 60);
        for (int i = 0; i < 20; i++) {
            double angle = random() * Math.PI * 2;
            double speed = 3.5 + random() * 5;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed - 6,
                          18 + (int)(random() * 10), color, 3f + (float)random(), 0));
        }
        for (int i = 0; i < 8; i++) {
            particles.add(new Particle(x + (random() - 0.5) * 20, y, (random() - 0.5) * 3,
                          -(5 + random() * 4), 20, new Color(255, 180, 30), 4f, 4));
        }
        shockwaves.add(new Shockwave(x, y, 80, 26, color, 4f));
        screenFlashes.add(new ScreenFlash(10, new Color(255, 240, 100)));
    }
    
    /** Spin slash effect */
    void spinSlash(double x, double y) {
        Color color = new Color(200, 70, 255);
        Color color2 = new Color(255, 100, 255);
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 28;
            double speed = 4 + random() * 4;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed,
                          22 + (int)(random() * 12), color, 3.2f, 2));
        }
        for (int i = 0; i < 18; i++) {
            double angle = random() * Math.PI * 2;
            double speed = 3 + random() * 5;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed - 2,
                          20 + (int)(random() * 10), new Color(255, 180, 255), 2.5f, 4));
        }
        shockwaves.add(new Shockwave(x, y, 100, 30, color, 5f));
        shockwaves.add(new Shockwave(x, y, 60, 22, color2, 3f));
        shockwaves.add(new Shockwave(x, y, 30, 14, Color.WHITE, 2f));
        screenFlashes.add(new ScreenFlash(16, new Color(180, 50, 255)));
    }
    
    /** Critical hit burst */
    void critBurst(double x, double y) {
        screenFlashes.add(new ScreenFlash(12, Color.WHITE));
        shockwaves.add(new Shockwave(x, y, 90, 28, new Color(255, 240, 80), 4.5f));
        for (int i = 0; i < 24; i++) {
            double angle = random() * Math.PI * 2;
            double speed = 4 + random() * 7;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed - 2,
                          20 + (int)(random() * 14), new Color(255, 220, 60), 4.5f, 0));
        }
    }
    
    /** Smoke effect */
    void smoke(double x, double y) {
        for (int i = 0; i < 5; i++) {
            particles.add(new Particle(x + (random() - 0.5) * 22, y, (random() - 0.5) * 1.5,
                          -(1 + random()), 32 + (int)(random() * 20), new Color(90, 90, 110),
                          13f + (float)random() * 8, 3));
        }
    }
    
    /** Phase change explosion */
    void phaseChange(double x, double y, Color color) {
        screenFlashes.add(new ScreenFlash(25, color));
        for (int i = 0; i < 36; i++) {
            double angle = i * Math.PI * 2 / 36;
            double speed = 5 + random() * 5;
            particles.add(new Particle(x, y, Math.cos(angle) * speed, Math.sin(angle) * speed,
                          30 + (int)(random() * 15), color, 4f, 2));
        }
        shockwaves.add(new Shockwave(x, y, 140, 35, color, 6f));
        shockwaves.add(new Shockwave(x, y, 80, 25, Color.WHITE, 4f));
    }
    
    /** Enemy hit effects */
    void enemyHit(double x, double y, boolean facingRight) {
        hitSparks(x, y, facingRight, new Color(80, 150, 255));
        bloodSplat(x, y, facingRight);
    }
    
    void addGhost(double x, double y, boolean facingRight, Object state, int attackFrame, 
                  int animFrame, boolean isPlayer, Weapon weapon, int comboStep) {
        ghosts.add(new Ghost(x, y, facingRight, state, attackFrame, animFrame, isPlayer, weapon, comboStep));
    }
    
    void addDamageNumber(double x, double y, int value, boolean critical) {
        damageNumbers.add(new DmgNumber(x - 10 + random() * 20, y, value, critical, critical ? "★" + value : null));
    }
}

// =====================================================================
//  ABSTRACT CHARACTER CLASS
// =====================================================================

/** Base class for all combat entities (Player and Enemy) */
abstract class Character {
    double x, y;                    // position
    double velX = 0, velY = 0;      // velocity
    int health, maxHealth;          // health values
    final int WIDTH = 40, HEIGHT = 100;  // hitbox dimensions
    int groundY;                    // ground level Y coordinate
    boolean facingRight = true;     // facing direction
    double knockbackX = 0;          // knockback force
    int knockbackTimer = 0;         // knockback duration
    int hurtFlashTimer = 0;         // hit flash duration
    
    Character(int x, int y, int hp) {
        this.x = x;
        this.y = y;
        this.maxHealth = hp;
        this.health = hp;
        this.groundY = y;
    }
    
    abstract void draw(Graphics2D g);
    abstract void update(List<Character> others, EffectManager effects);
    
    /** Get hitbox rectangle for collision detection */
    Rectangle getHitbox() {
        return new Rectangle((int)x - WIDTH/2, (int)y - HEIGHT, WIDTH, HEIGHT);
    }
    
    /** Apply damage to character */
    void takeDamage(int damage) {
        health = Math.max(0, health - damage);
        hurtFlashTimer = 16;
    }
    
    /** Apply knockback force */
    void applyKnockback(double force) {
        knockbackX = force;
        knockbackTimer = 14;
    }
    
    boolean isAlive() { return health > 0; }
    
    /** Physics update with boundaries */
    void physicsStep(int leftBound, int rightBound) {
        if (knockbackTimer > 0) {
            x += knockbackX;
            knockbackX *= 0.72;
            knockbackTimer--;
        }
        x += velX;
        y += velY;
        velY += 0.65;  // gravity
        
        if (y >= groundY) {
            y = groundY;
            velY = 0;
        }
        x = Math.max(leftBound, Math.min(rightBound, x));
        if (hurtFlashTimer > 0) hurtFlashTimer--;
    }
    
    /** Draw health bar above character */
    void drawHealthBar(Graphics2D g, int barWidth, int barHeight) {
        int barX = (int)x - barWidth/2;
        int barY = (int)y - HEIGHT - barHeight - 9;
        
        // Background (dark red)
        g.setColor(new Color(40, 0, 0));
        g.fillRect(barX, barY, barWidth, barHeight);
        
        // Health fill (color based on percentage)
        float percent = health / (float)maxHealth;
        Color fillColor = percent > 0.5f ? new Color(40, 200, 60) : 
                         (percent > 0.25f ? new Color(230, 180, 0) : new Color(220, 40, 40));
        g.setColor(fillColor);
        g.fillRect(barX, barY, (int)(barWidth * percent), barHeight);
        
        // Highlight effect
        g.setColor(new Color(255, 255, 255, 55));
        g.fillRect(barX, barY, (int)(barWidth * percent), barHeight/2);
        
        // Border
        g.setColor(new Color(180, 180, 180, 80));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(barX, barY, barWidth, barHeight);
    }
}

// =====================================================================
//  PLAYER CLASS — Combo-based combat system
// =====================================================================

/** Playable character with 4-stage combo attack system */
class Player extends Character {
    // Animation and combo data arrays
    static final int[] ATTACK_DURATION = {13, 13, 18, 28};      // frames per attack
    static final int[] COMBO_WINDOW = {24, 24, 30, 65};         // window to chain next attack
    static final String[] ATTACK_NAMES = {"JAB", "CROSS", "UPPERCUT", "SPIN SLASH"};
    static final int[] DAMAGE_MULTIPLIER = {1, 1, 2, 3};
    static final double[] KNOCKBACK_MULTIPLIER = {1.0, 1.4, 0.0, 1.8};
    
    enum State { IDLE, WALK, JUMP, ATTACK }
    
    State state = State.IDLE;
    Weapon weapon = new Fists();
    int comboStep = 0;          // 0-3 current combo stage
    int attackFrame = 0;        // current frame of attack animation
    boolean hitLanded = false;   // whether damage already dealt this attack
    boolean attacking = false;
    int comboWindowTimer = 0;    // remaining frames to chain next attack
    int comboCount = 0;          // consecutive hits landed
    int comboTimer = 0;          // how long combo counter stays visible
    String critText = "";
    int critTimer = 0;
    int ghostTick = 0;
    int animTick = 0, animFrame = 0;
    int panelWidth;
    boolean nextAttackQueued = false;
    
    Player(int x, int y, int panelWidth) {
        super(x, y, 100);
        this.panelWidth = panelWidth;
    }
    
    void setWeapon(Weapon w) { weapon = w; }
    
    /** Trigger attack - chains into combo if within window */
    void triggerAttack() {
        if (attacking) {
            nextAttackQueued = true;
            return;
        }
        if (comboWindowTimer == 0) comboStep = 0;
        startSwing();
    }
    
    private void startSwing() {
        attacking = true;
        attackFrame = 0;
        hitLanded = false;
        nextAttackQueued = false;
        state = State.ATTACK;
        animFrame = 0;
        animTick = 0;
        ghostTick = 0;
    }
    
    /** Calculate attack hitbox based on weapon and combo stage */
    Rectangle getAttackBox() {
        int range = weapon.getRange() + (comboStep == 3 ? 24 : 0);
        int height = 36 + (comboStep == 2 ? 24 : 0);
        int yOffset = (int)y - HEIGHT + (comboStep == 2 ? 2 : 14);
        
        if (comboStep == 3) {
            return new Rectangle((int)x - range, (int)y - HEIGHT + 8, range * 2, height);
        }
        return facingRight ? new Rectangle((int)x + 12, yOffset, range, height) :
                            new Rectangle((int)x - 12 - range, yOffset, range, height);
    }
    
    /** Check if attack hits enemy and apply damage */
    void checkHit(Character target, EffectManager effects) {
        if (!hitLanded && attacking && attackFrame >= 5 && attackFrame <= ATTACK_DURATION[comboStep] - 2) {
            if (getAttackBox().intersects(target.getHitbox())) {
                hitLanded = true;
                
                // Critical hit calculation
                boolean critical = Math.random() < weapon.getCritChance() * (comboStep == 3 ? 2 : 1);
                int baseDamage = weapon.getBaseDamage() * DAMAGE_MULTIPLIER[comboStep];
                int damage = critical ? baseDamage * 2 : baseDamage;
                
                target.takeDamage(damage);
                
                // Knockback
                double knockback = KNOCKBACK_MULTIPLIER[comboStep] * (facingRight ? 1 : -1) * (8 + comboStep * 2.5);
                target.applyKnockback(knockback);
                if (comboStep == 2) target.velY = -13;  // uppercut launches enemy
                
                // Combo tracking
                comboCount++;
                comboTimer = 110;
                if (critical) {
                    critText = "CRITICAL!";
                    critTimer = 45;
                }
                
                // Visual effects based on hit location
                double hitX = target.x + (facingRight ? -24 : 24);
                double hitY = target.y - target.HEIGHT * 0.5;
                Color hitColor = comboStep == 3 ? new Color(200, 80, 255) :
                                (comboStep == 2 ? new Color(255, 230, 60) :
                                (comboStep == 1 ? new Color(255, 160, 50) : new Color(255, 100, 60)));
                
                effects.hitSparks(hitX, hitY, facingRight, hitColor);
                effects.bloodSplat(hitX, hitY, facingRight);
                effects.slashTrail(x, hitY, facingRight, weapon);
                effects.addDamageNumber(hitX, hitY - 14, damage, critical);
                
                // Special effects per combo stage
                if (comboStep == 1) effects.groundSlam(hitX, target.y, facingRight);
                if (comboStep == 2) effects.uppercut(hitX, hitY);
                if (comboStep == 3) effects.spinSlash(x, (int)y - HEIGHT/2);
                if (critical) effects.critBurst(hitX, hitY);
                
                // Sound effects
                SoundEngine.play(comboStep == 2 ? 3 : comboStep == 3 ? 4 : comboStep == 1 ? 2 : 0);
                if (critical) SoundEngine.play(10);
                if (comboStep > 0) SoundEngine.play(13);
            }
        }
    }
    
    @Override
    void update(List<Character> others, EffectManager effects) {
        // Update combo timers
        if (comboTimer > 0) comboTimer--;
        else comboCount = 0;
        if (critTimer > 0) critTimer--;
        
        // Attack state update
        if (attacking) {
            attackFrame++;
            velX *= 0.7;
            ghostTick++;
            
            // Create ghost trail for higher combo stages
            if (ghostTick >= 3 && comboStep >= 2) {
                ghostTick = 0;
                effects.addGhost(x, y, facingRight, state, attackFrame, animFrame, true, weapon, comboStep);
            }
            if (comboStep == 3 && attackFrame % 4 == 0) effects.smoke(x, y - HEIGHT/2.0);
            
            if (attackFrame >= ATTACK_DURATION[comboStep]) {
                boolean chain = nextAttackQueued && comboStep < 3;
                attacking = false;
                hitLanded = false;
                nextAttackQueued = false;
                if (chain) {
                    comboStep++;
                    comboWindowTimer = 0;
                    startSwing();
                } else {
                    state = State.IDLE;
                    attackFrame = 0;
                    comboWindowTimer = 0;
                    if (comboStep >= 3) comboStep = 0;
                }
            } else {
                comboWindowTimer = COMBO_WINDOW[comboStep];
            }
        } else {
            if (comboWindowTimer > 0) {
                comboWindowTimer--;
                if (comboWindowTimer == 0) comboStep = 0;
            }
        }
        
        // Walking animation update
        animTick++;
        if (animTick >= (state == State.WALK ? 5 : 8)) {
            animTick = 0;
            animFrame = (animFrame + 1) % (state == State.WALK ? 4 : 1);
        }
        
        physicsStep(32, panelWidth - 32);
    }
    
    @Override
    void draw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D)g.create();
        if (!facingRight) {
            g2.translate((int)x * 2, 0);
            g2.scale(-1, 1);
        }
        
        // Hurt flash effect
        if (hurtFlashTimer > 0 && hurtFlashTimer % 4 < 2) {
            g2.setColor(new Color(255, 80, 80, 150));
            g2.fillRect((int)x - WIDTH/2 - 2, (int)y - HEIGHT - 2, WIDTH + 4, HEIGHT + 4);
        }
        
        drawStickman(g2, (int)x, (int)y, state, attackFrame, animFrame, weapon, true, comboStep);
        g2.dispose();
        
        drawHealthBar(g, 48, 7);
        int tx = (int)x;
        
        // Attack name display
        if (attacking) {
            Color labelColor = comboStep == 3 ? new Color(220, 100, 255) :
                              (comboStep == 2 ? new Color(255, 230, 60) :
                              (comboStep == 1 ? new Color(255, 170, 50) : new Color(255, 110, 60)));
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.setColor(new Color(0, 0, 0, 120));
            g.drawString(ATTACK_NAMES[comboStep], tx - 28 + 1, (int)y - HEIGHT - 23 + 1);
            g.setColor(labelColor);
            g.drawString(ATTACK_NAMES[comboStep], tx - 28, (int)y - HEIGHT - 23);
        }
        
        // Combo counter
        if (comboCount > 1 && comboTimer > 0) {
            g.setFont(new Font("Arial", Font.BOLD, 17));
            g.setColor(new Color(80, 170, 255));
            g.drawString("COMBO x" + comboCount, tx - 36, (int)y - HEIGHT - 40);
        }
        
        // Critical text
        if (critTimer > 0) {
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.setColor(new Color(255, 215, 30));
            g.drawString(critText, tx - 38, (int)y - HEIGHT - 60);
        }
        
        // Weapon display
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(200, 200, 200, 140));
        g.drawString("[" + weapon.getName() + "]", (int)x - 18, (int)y - HEIGHT - 11);
        
        // Combo stage dots
        int dotX = (int)x - 14, dotY = (int)y - HEIGHT - 74;
        for (int i = 0; i < 4; i++) {
            boolean lit = i < comboStep || (attacking && i == comboStep);
            Color dotColor = lit ? (comboStep == 3 ? new Color(200, 80, 255) :
                                   (comboStep == 2 ? new Color(255, 230, 60) : new Color(255, 140, 40))) :
                                 new Color(45, 50, 70);
            g.setColor(dotColor);
            g.fillOval(dotX + i * 9, dotY, 7, 7);
            g.setColor(new Color(200, 200, 200, 70));
            g.drawOval(dotX + i * 9, dotY, 7, 7);
        }
        
        // Combo window timer bar
        if (comboWindowTimer > 0 && !attacking && comboStep > 0) {
            int barWidth = 70;
            float percent = comboWindowTimer / (float)COMBO_WINDOW[comboStep - 1];
            int barX = (int)x - barWidth/2;
            int barY = (int)y - HEIGHT - 82;
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(barX, barY, barWidth, 5);
            g.setColor(new Color(255, 200, 60, (int)(200 * percent)));
            g.fillRect(barX, barY, (int)(barWidth * percent), 5);
        }
    }
    
    /** Static method to draw stick figure character with animations */
    static void drawStickman(Graphics2D g, int cx, int cy, Object stateObj, int attackFrame, 
                              int animFrame, Weapon weapon, boolean isPlayer, int comboStep) {
        boolean isAttacking = stateObj.toString().contains("ATTACK");
        boolean isWalking = stateObj.toString().contains("WALK");
        boolean isJumping = stateObj.toString().contains("JUMP");
        
        float legSwing = isWalking ? (float)Math.sin(animFrame * Math.PI/2) * 28 : 0;
        float armSwing = isWalking ? (float)-Math.sin(animFrame * Math.PI/2) * 20 : 0;
        
        Color bodyColor = isPlayer ? new Color(255, 0, 0) : new Color(60, 60, 60);
        Color limbColor = isPlayer ? new Color(255, 0, 0) : new Color(70, 70, 70);
        Color headColor = isPlayer ? new Color(255, 0, 0) : new Color(60, 60, 60);
        
        int headY = cy - 100;
        int bodyTop = headY + 24;
        int bodyBottom = cy - 22;
        int midArm = bodyTop + 18;
        
        g.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // Draw legs
        g.setColor(limbColor);
        if (isJumping) {
            g.drawLine(cx, bodyBottom, cx + 20, bodyBottom + 18);
            g.drawLine(cx, bodyBottom, cx - 20, bodyBottom + 18);
        } else if (isAttacking) {
            // Attack-specific leg poses
            if (comboStep == 2) {
                g.drawLine(cx, bodyBottom, cx + 24, cy - 2);
                g.drawLine(cx, bodyBottom, cx - 14, cy - 2);
            } else if (comboStep == 3) {
                double angle = attackFrame * 0.30;
                g.drawLine(cx, bodyBottom, cx + (int)(26 * Math.cos(angle)), cy - 2);
                g.drawLine(cx, bodyBottom, cx - (int)(26 * Math.cos(angle)), cy - 2);
            } else if (comboStep == 1) {
                g.drawLine(cx, bodyBottom, cx + 22, cy - 2);
                g.drawLine(cx, bodyBottom, cx - 10, cy - 2);
            } else {
                g.drawLine(cx, bodyBottom, cx + 16, cy - 2);
                g.drawLine(cx, bodyBottom, cx - 16, cy - 2);
            }
        } else {
            double legAngle = Math.toRadians(legSwing);
            g.drawLine(cx, bodyBottom, cx + (int)(22 * Math.sin(legAngle)), cy - 2);
            g.drawLine(cx, bodyBottom, cx - (int)(22 * Math.sin(legAngle)), cy - 2);
        }
        
        // Draw body
        g.setColor(bodyColor);
        if (isAttacking && comboStep == 2) {
            g.drawLine(cx - 3, bodyTop, cx + 2, bodyBottom);
        } else if (isAttacking && comboStep == 3) {
            double angle = attackFrame * 0.18;
            g.drawLine(cx + (int)(5 * Math.sin(angle)), bodyTop, cx - (int)(5 * Math.sin(angle)), bodyBottom);
        } else if (isAttacking && comboStep == 1) {
            g.drawLine(cx + 2, bodyTop, cx - 2, bodyBottom);
        } else {
            g.drawLine(cx, bodyTop, cx, bodyBottom);
        }
        
        // Draw arms
        g.setColor(limbColor);
        if (isAttacking) {
            float progress = Math.min(1f, attackFrame / 7f);
            if (attackFrame > ATTACK_DURATION[comboStep] / 2) {
                progress = 1f - (attackFrame - ATTACK_DURATION[comboStep]/2) * 2f / ATTACK_DURATION[comboStep];
            }
            progress = Math.max(0, progress);
            
            if (comboStep == 2) {  // Uppercut
                int upX = cx + (int)(6 * progress);
                int upY = bodyTop - (int)(50 * progress);
                g.drawLine(cx, midArm, upX, upY);
                g.drawLine(cx, midArm, cx - 22, midArm + 18);
                g.setColor(new Color(255, 230, 60, (int)(220 * progress)));
                g.fillOval(upX - 9, upY - 9, 18, 18);
            } else if (comboStep == 3) {  // Spin slash
                double angle = attackFrame * 0.28;
                int arm1X = cx + (int)(42 * Math.cos(angle));
                int arm1Y = midArm + (int)(16 * Math.sin(angle));
                int arm2X = cx - (int)(42 * Math.cos(angle));
                int arm2Y = midArm - (int)(16 * Math.sin(angle));
                g.drawLine(cx, midArm, arm1X, arm1Y);
                g.drawLine(cx, midArm, arm2X, arm2Y);
                if (weapon instanceof Sword) {
                    g.setColor(new Color(210, 160, 255));
                    g.setStroke(new BasicStroke(3.5f));
                    g.drawLine(arm1X, arm1Y, arm1X + (int)(24 * Math.cos(angle + 0.6)), arm1Y - (int)(9 * Math.sin(angle)));
                    g.drawLine(arm2X, arm2Y, arm2X - (int)(24 * Math.cos(angle + 0.6)), arm2Y + (int)(9 * Math.sin(angle)));
                }
            } else if (comboStep == 1) {  // Cross
                int punchX = cx + (int)(weapon.getRange() * 0.88 * progress);
                int punchY = midArm + (int)(16 * progress) - (int)(4 * progress);
                g.drawLine(cx, midArm, punchX, punchY);
                g.drawLine(cx, midArm, cx - 20, midArm - 8);
                if (weapon instanceof Sword) {
                    g.setColor(new Color(200, 220, 255));
                    g.setStroke(new BasicStroke(3.5f));
                    int swordX = punchX + (int)(24 * progress);
                    g.drawLine(punchX, punchY, swordX, punchY - 6);
                    g.fillOval(swordX - 5, punchY - 10, 9, 9);
                } else {
                    g.setColor(new Color(240, 190, 130));
                    g.fillOval(punchX - 8, punchY - 8, 16, 16);
                }
            } else {  // JAB
                int punchX = cx + (int)(weapon.getRange() * 0.82 * progress);
                int punchY = midArm - (int)(12 * progress);
                g.drawLine(cx, midArm, punchX, punchY);
                g.drawLine(cx, midArm, cx - 22, midArm + 14);
                if (weapon instanceof Sword) {
                    g.setColor(new Color(200, 220, 255));
                    g.setStroke(new BasicStroke(3.5f));
                    int swordX = punchX + (int)(22 * progress);
                    g.drawLine(punchX, punchY, swordX, punchY - 8);
                    g.fillOval(swordX - 4, punchY - 12, 9, 9);
                } else {
                    g.setColor(new Color(235, 190, 130));
                    g.fillOval(punchX - 8, punchY - 8, 16, 16);
                }
            }
        } else {
            double armAngle = Math.toRadians(armSwing);
            int armX = cx + (int)(26 * Math.sin(armAngle));
            int armY = midArm + 18 + (int)(6 * Math.cos(armAngle));
            g.drawLine(cx, midArm, armX, armY);
            g.drawLine(cx, midArm, cx - (int)(26 * Math.sin(armAngle)), armY);
            if (weapon instanceof Sword) {
                g.setColor(new Color(190, 210, 240, 180));
                g.setStroke(new BasicStroke(3f));
                g.drawLine(armX, armY, armX + 20, armY - 9);
            }
        }
        
        // Draw head
        g.setColor(headColor);
        g.setStroke(new BasicStroke(2f));
        int headOffsetX = (isAttacking && comboStep == 2) ? (int)(5 * Math.min(1f, attackFrame/8f)) : 0;
        g.fillOval(cx - 13 + headOffsetX, headY, 26, 26);
        
        // Cape/shoulder decoration for player
        if (isPlayer) {
            g.setColor(new Color(180, 30, 30, 185));
            int[] spineX = {cx, cx - 6, cx - 15, cx - 4};
            int[] spineY = {bodyTop, bodyTop + 12, bodyTop + 28, bodyTop + 9};
            g.fillPolygon(spineX, spineY, 4);
        }
    }
    
    static void drawStickman(Graphics2D g, int cx, int cy, Object state, int af, int animf, 
                              Weapon w, boolean isPlayer) {
        drawStickman(g, cx, cy, state, af, animf, w, isPlayer, 0);
    }
}

// =====================================================================
//  ENEMY CLASS — 1000 HP 3-Phase Boss
// =====================================================================

/** Boss enemy with 1000 HP and 3 escalating phases */
class Enemy extends Character {
    enum State { IDLE, WALK, JUMP, ATTACK, DASH, RAGE }
    
    State state = State.IDLE;
    int attackFrame = 0;
    int attackCooldown = 0;
    int animTick = 0, animFrame = 0;
    int thinkTimer = 25;
    int jumpCooldown = 0;
    int dashCooldown = 0;
    int phase = 1;              // 1, 2, or 3
    int panelWidth;
    int rageTick = 0;           // Phase 3 rage attack timer
    
    Enemy(int x, int y, int panelWidth) {
        super(x, y, 1000);      // 1000 HP boss
        this.panelWidth = panelWidth;
        facingRight = false;
    }
    
    /** Determine current phase based on health percentage */
    int getPhase() {
        float percent = health / (float)maxHealth;
        if (percent > 0.66f) return 1;
        if (percent > 0.33f) return 2;
        return 3;
    }
    
    double getSpeed() {
        return phase == 3 ? 5.2 : phase == 2 ? 4.0 : 2.9;
    }
    
    int getAttackDamage() {
        return phase == 3 ? 22 + (int)(Math.random() * 10) :
               phase == 2 ? 16 + (int)(Math.random() * 8) :
                            10 + (int)(Math.random() * 6);
    }
    
    int getAttackCooldown() {
        return phase == 3 ? 18 : phase == 2 ? 28 : 50;
    }
    
    Rectangle getAttackBox() {
        int range = phase == 3 ? 55 : phase == 2 ? 44 : 36;
        int height = 36;
        int yOffset = (int)y - HEIGHT + 12;
        
        if (phase == 3) {
            return new Rectangle((int)x - range/2, (int)y - HEIGHT + 8, range, height + 16);
        }
        return facingRight ? new Rectangle((int)x + 12, yOffset, range, height) :
                            new Rectangle((int)x - 12 - range, yOffset, range, height);
    }
    
    @Override
    void update(List<Character> others, EffectManager effects) {
        // Update cooldowns
        if (attackCooldown > 0) attackCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        if (dashCooldown > 0) dashCooldown--;
        
        // Check for phase transition
        int newPhase = getPhase();
        if (newPhase != phase) {
            phase = newPhase;
            SoundEngine.play(14);
            Color phaseColor = phase == 2 ? new Color(255, 150, 0) : new Color(255, 30, 30);
            effects.phaseChange(x, y - HEIGHT/2.0, phaseColor);
        }
        
        // Find player target
        Character player = others.stream().filter(c -> c instanceof Player).findFirst().orElse(null);
        if (player == null || !isAlive() || !player.isAlive()) return;
        
        double dx = player.x - x;
        facingRight = dx > 0;
        double distance = Math.abs(dx);
        double speed = getSpeed();
        
        // State machine for enemy AI
        if (state == State.ATTACK) {
            attackFrame++;
            velX *= 0.75;
            
            if (attackFrame >= (phase == 3 ? 16 : 22)) {
                state = State.IDLE;
                attackFrame = 0;
                
                if (getAttackBox().intersects(player.getHitbox())) {
                    int damage = getAttackDamage();
                    player.takeDamage(damage);
                    player.applyKnockback(facingRight ? 10 : -10);
                    double hitX = player.x + (facingRight ? -22 : 22);
                    double hitY = player.y - player.HEIGHT * 0.5;
                    effects.enemyHit(hitX, hitY, facingRight);
                    effects.addDamageNumber(hitX, hitY - 12, damage, false);
                    SoundEngine.play(5);
                }
                attackCooldown = getAttackCooldown();
            }
        } else if (state == State.DASH) {
            velX = facingRight ? speed * 2.5 : -(speed * 2.5);
            if (distance <= 68) {
                state = State.ATTACK;
                attackFrame = 0;
                velX = 0;
            } else if (Math.abs(velX) < 0.6) {
                state = State.IDLE;
            }
        } else if (state == State.RAGE) {
            // Phase 3 special: rapid multi-hit attacks
            rageTick++;
            velX = 0;
            if (rageTick % 8 == 0 && rageTick < 50) {
                if (getAttackBox().intersects(player.getHitbox())) {
                    int damage = 12 + (int)(Math.random() * 8);
                    player.takeDamage(damage);
                    player.applyKnockback(facingRight ? 6 : -6);
                    double hitX = player.x + (facingRight ? -22 : 22);
                    double hitY = player.y - player.HEIGHT * 0.5;
                    effects.enemyHit(hitX, hitY, facingRight);
                    effects.addDamageNumber(hitX, hitY - 12, damage, false);
                    SoundEngine.play(2);
                }
            }
            if (rageTick >= 50) {
                state = State.IDLE;
                rageTick = 0;
                attackCooldown = 35;
            }
        } else {
            // Idle/Walk decision making
            thinkTimer--;
            if (thinkTimer <= 0) {
                int thinkDelay = phase == 3 ? 8 + (int)(Math.random() * 10) :
                                phase == 2 ? 14 + (int)(Math.random() * 14) :
                                             22 + (int)(Math.random() * 18);
                thinkTimer = thinkDelay;
                
                if (distance <= 68 && attackCooldown == 0) {
                    if (phase == 3 && Math.random() < 0.3) {
                        state = State.RAGE;
                        rageTick = 0;
                    } else {
                        state = State.ATTACK;
                        attackFrame = 0;
                    }
                } else if (distance > 68) {
                    double dashChance = phase == 3 ? 0.6 : phase == 2 ? 0.4 : 0.15;
                    if (dashCooldown == 0 && Math.random() < dashChance) {
                        state = State.DASH;
                        dashCooldown = 60;
                    } else {
                        state = State.WALK;
                        velX = (dx > 0 ? 1 : -1) * speed;
                    }
                } else {
                    velX = (Math.random() < 0.5 ? 1 : -1) * 1.5;
                    state = State.WALK;
                }
                
                double jumpChance = phase == 3 ? 0.55 : phase == 2 ? 0.42 : 0.28;
                if (distance < 180 && jumpCooldown == 0 && Math.random() < jumpChance && y >= groundY) {
                    velY = phase == 3 ? -15 : phase == 2 ? -13.5 : -12;
                    state = State.JUMP;
                    jumpCooldown = phase == 3 ? 40 : 60;
                }
            }
            
            if (state == State.WALK) {
                velX = (dx > 0 ? 1 : -1) * speed;
            } else if (state == State.IDLE) {
                velX *= 0.5;
            }
        }
        
        if (state == State.JUMP) {
            velX = (dx > 0 ? 1 : -1) * (speed * 1.2);
            if (y >= groundY) state = State.IDLE;
        }
        
        // Animation update
        animTick++;
        if (animTick >= (state == State.WALK || state == State.DASH ? 5 : 8)) {
            animTick = 0;
            animFrame = (animFrame + 1) % (state == State.WALK || state == State.DASH ? 4 : 1);
        }
        
        physicsStep(32, panelWidth - 32);
    }
    
    @Override
    void draw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D)g.create();
        if (!facingRight) {
            g2.translate((int)x * 2, 0);
            g2.scale(-1, 1);
        }
        
        // Hurt flash
        if (hurtFlashTimer > 0 && hurtFlashTimer % 4 < 2) {
            g2.setColor(new Color(255, 80, 80, 150));
            g2.fillRect((int)x - WIDTH/2 - 2, (int)y - HEIGHT - 2, WIDTH + 4, HEIGHT + 4);
        }
        
        // Phase visual effects
        if (phase == 3) {
            g2.setColor(new Color(255, 20, 20, 70));
            g2.setStroke(new BasicStroke(8f));
            g2.drawOval((int)x - 24, (int)y - HEIGHT - 6, 48, HEIGHT + 12);
        } else if (phase == 2) {
            g2.setColor(new Color(255, 140, 0, 50));
            g2.setStroke(new BasicStroke(5f));
            g2.drawOval((int)x - 22, (int)y - HEIGHT - 4, 44, HEIGHT + 8);
        }
        
        Player.drawStickman(g2, (int)x, (int)y, state, attackFrame, animFrame, new Fists(), false);
        g2.dispose();
        
        // Boss health bar (wider)
        drawHealthBar(g, 120, 9);
        
        // Phase label
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        Color phaseColor = phase == 3 ? new Color(255, 40, 40, 220) :
                          phase == 2 ? new Color(255, 150, 40, 210) :
                                       new Color(200, 80, 80, 180);
        g.setColor(phaseColor);
        String phaseLabel = phase == 3 ? "BOSS [RAGE]" : phase == 2 ? "BOSS [P2]" : "BOSS";
        g.drawString(phaseLabel, (int)x - 28, (int)y - HEIGHT - 12);
        
        // Rage indicator
        if (state == State.RAGE) {
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.setColor(new Color(255, 50, 50));
            g.drawString("RAGE!!", (int)x - 18, (int)y - HEIGHT - 28);
        }
    }
}

// =====================================================================
//  LAB BACKGROUND — Sci-fi Laboratory Environment
// =====================================================================

/** Cached background image with laboratory theme */
class LabBackground {
    private BufferedImage cache;
    private int cachedWidth, cachedHeight;
    
    BufferedImage get(int width, int height) {
        if (cache == null || cachedWidth != width || cachedHeight != height) {
            cachedWidth = width;
            cachedHeight = height;
            cache = buildBackground(width, height);
        }
        return cache;
    }
    
    private BufferedImage buildBackground(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int floorY = h - 100;
        
        // Dark walls
        g.setColor(new Color(10, 12, 22));
        g.fillRect(0, 0, w, floorY);
        
        // Wall panels
        g.setColor(new Color(18, 22, 38));
        for (int px = 10; px < w - 10; px += 90) {
            g.fillRect(px, 20, 80, floorY - 30);
        }
        g.setColor(new Color(30, 36, 60));
        g.setStroke(new BasicStroke(1f));
        for (int px = 10; px < w - 10; px += 90) {
            g.drawRect(px, 20, 80, floorY - 30);
        }
        
        // Horizontal lines
        g.setColor(new Color(40, 50, 80));
        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(0, 80, w, 80);
        g.drawLine(0, 180, w, 180);
        g.setColor(new Color(50, 62, 95));
        g.setStroke(new BasicStroke(5f));
        g.drawLine(0, 82, w, 82);
        g.drawLine(0, 182, w, 182);
        
        // Bolts
        g.setColor(new Color(60, 75, 110));
        for (int bx = 30; bx < w; bx += 60) {
            g.fillOval(bx - 5, 76, 10, 10);
            g.fillOval(bx - 5, 176, 10, 10);
        }
        
        // Laboratory tanks
        drawTank(g, 60, 120, 60, 160, new Color(0, 200, 100, 120));
        drawTank(g, 150, 110, 60, 170, new Color(100, 0, 220, 100));
        drawTank(g, w - 130, 115, 60, 165, new Color(0, 140, 220, 110));
        drawTank(g, w - 60, 120, 55, 160, new Color(220, 80, 0, 100));
        
        // Warning sign
        drawWarning(g, w/2 - 50, 30, 100, 55);
        
        // Text labels
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.setColor(new Color(60, 80, 120, 160));
        g.drawString("WING A7", w/2 - 40, 24);
        
        g.setFont(new Font("Arial", Font.BOLD, 11));
        g.setColor(new Color(180, 30, 30, 180));
        g.drawString("FREEDOM IS NOT A SYMPTOM.", 20, floorY - 60);
        
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(new Color(150, 150, 30, 160));
        g.drawString("THEY TRIED TO BREAK US.", w - 190, floorY - 70);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.setColor(new Color(60, 200, 80, 180));
        g.drawString("WE EVOLVE.", w - 170, floorY - 55);
        
        // Data panels
        drawMonitor(g, w - 110, floorY - 110, 90, 70);
        drawSystemPanel(g, 12, floorY - 120, 110, 90);
        
        // Floor grid
        g.setColor(new Color(22, 26, 42));
        g.fillRect(0, floorY, w, 100);
        g.setStroke(new BasicStroke(0.5f));
        for (int fx = 0; fx < w; fx += 50) {
            for (int fy = floorY; fy < h; fy += 25) {
                g.setColor(new Color(35, 42, 65));
                g.drawRect(fx, fy, 50, 25);
            }
        }
        
        // Caution stripes
        g.setStroke(new BasicStroke(4f));
        for (int cs = 0; cs < 8; cs++) {
            g.setColor(cs % 2 == 0 ? new Color(200, 160, 0, 120) : new Color(0, 0, 0, 60));
            g.fillRect(cs * 14, floorY, 14, 8);
            g.fillRect(w - 112 + cs * 14, floorY, 14, 8);
        }
        
        g.dispose();
        return img;
    }
    
    private void drawTank(Graphics2D g, int cx, int y, int tw, int th, Color glow) {
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 30));
        g.fillOval(cx - tw/2 - 15, y - 15, tw + 30, th + 30);
        g.setColor(new Color(20, 25, 45));
        g.fillRoundRect(cx - tw/2, y, tw, th, 10, 10);
        g.setColor(glow);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx - tw/2, y, tw, th, 10, 10);
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 50));
        g.fillRoundRect(cx - tw/2 + 3, y + th/3, tw - 6, th*2/3 - 3, 8, 8);
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 160));
        int hx = cx, hy = y + th/3;
        g.setStroke(new BasicStroke(2f));
        g.drawOval(hx - 6, hy, 12, 12);
        g.drawLine(hx, hy + 12, hx, hy + 28);
        g.drawLine(hx, hy + 16, hx - 8, hy + 24);
        g.drawLine(hx, hy + 16, hx + 8, hy + 24);
        g.drawLine(hx, hy + 28, hx - 7, hy + 42);
        g.drawLine(hx, hy + 28, hx + 7, hy + 42);
        g.setColor(new Color(255, 255, 255, 28));
        g.fillRoundRect(cx - tw/2 + 4, y + 4, 8, th - 8, 4, 4);
    }
    
    private void drawWarning(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(200, 160, 0, 100));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(220, 180, 0, 160));
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x, y, w, h);
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(new Color(255, 220, 0, 200));
        g.drawString("!! HAZARDOUS !!", x + 6, y + 18);
        g.drawString("EXPERIMENTAL ZONE", x + 2, y + 32);
        g.drawString("AUTH. PERSONNEL ONLY", x + 1, y + 46);
    }
    
    private void drawMonitor(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(15, 20, 35));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(0, 120, 200));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(x, y, w, h);
        g.setColor(new Color(0, 180, 80, 200));
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.drawString("> SUBJECT DATA", x + 4, y + 14);
        g.drawString("> VITALS: OK", x + 4, y + 24);
        g.drawString("> PHASE: COMBAT", x + 4, y + 34);
        g.drawString("> SECTOR: A7", x + 4, y + 44);
        g.setColor(new Color(0, 255, 100, 15));
        for (int sl = y; sl < y + h; sl += 3) {
            g.drawLine(x, sl, x + w, sl);
        }
    }
    
    private void drawSystemPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(10, 40, 20, 200));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(0, 180, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(x, y, w, h);
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(new Color(0, 220, 80));
        g.drawString("SYSTEM ONLINE", x + 4, y + 14);
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.drawString("SUBJECTS: 0", x + 4, y + 26);
        g.drawString("STATUS: STABLE", x + 4, y + 36);
        g.setColor(new Color(0, 255, 60));
        g.fillOval(x + 6, y + 46, 7, 7);
        g.setColor(new Color(0, 200, 60, 180));
        g.setStroke(new BasicStroke(1.2f));
        int[] ecgX = {x+4, x+14, x+18, x+24, x+30, x+36, x+46, x+50, x+60, x+70, x+80, x+100};
        int[] ecgY = {y+70, y+70, y+58, y+78, y+60, y+75, y+75, y+60, y+80, y+65, y+75, y+75};
        g.drawPolyline(ecgX, ecgY, ecgX.length);
    }
}

// =====================================================================
//  VIDEO CUTSCENE PLAYER — Import custom frame sequences
// =====================================================================

/** Plays either imported video frames or built-in cutscene */
class VideoCutscene {
    List<BufferedImage> frames = new ArrayList<>();
    private int frameIndex = 0, frameTick = 0;
    private int fps = 24;
    private int ticksPerFrame;
    boolean hasVideo = false, done = false;
    
    // Built-in fallback cutscene
    private int builtinFrame = 0, builtinTick = 0;
    private static final int BUILTIN_TICKS = 150, BUILTIN_TOTAL = 6;
    
    VideoCutscene() {
        ticksPerFrame = Math.max(1, 60 / fps);
    }
    
    void reset() {
        frameIndex = 0;
        frameTick = 0;
        done = false;
        builtinFrame = 0;
        builtinTick = 0;
    }
    
    boolean loadFolder(File folder) {
        frames.clear();
        File[] files = folder.listFiles(f -> {
            String name = f.getName().toLowerCase();
            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp");
        });
        if (files == null || files.length == 0) return false;
        java.util.Arrays.sort(files);
        for (File f : files) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) frames.add(img);
            } catch (Exception ignored) {}
        }
        hasVideo = !frames.isEmpty();
        frameIndex = 0;
        frameTick = 0;
        return hasVideo;
    }
    
    boolean loadFile(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                frames.clear();
                frames.add(img);
                hasVideo = true;
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
    
    void setFPS(int f) {
        fps = Math.max(1, Math.min(60, f));
        ticksPerFrame = Math.max(1, 60 / fps);
    }
    
    void skip() { done = true; }
    
    void update() {
        if (done) return;
        if (hasVideo) {
            frameTick++;
            if (frameTick >= ticksPerFrame) {
                frameTick = 0;
                frameIndex++;
                if (frameIndex >= frames.size()) done = true;
            }
        } else {
            builtinTick++;
            if (builtinTick >= BUILTIN_TICKS) {
                builtinTick = 0;
                builtinFrame++;
                if (builtinFrame >= BUILTIN_TOTAL) done = true;
            }
        }
    }
    
    void draw(Graphics2D g, int width, int height, LabBackground lab) {
        int barHeight = 70;
        if (hasVideo && !frames.isEmpty() && frameIndex < frames.size()) {
            BufferedImage frame = frameIndex < frames.size() ? frames.get(frameIndex) : frames.get(frames.size() - 1);
            g.drawImage(frame, 0, 0, width, height, null);
            
            // Cinematic letterbox bars
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, barHeight);
            g.fillRect(0, height - barHeight, width, barHeight);
            
            // Progress bar
            float progress = (float)frameIndex / Math.max(1, frames.size() - 1);
            g.setColor(new Color(60, 60, 80));
            g.fillRect(width/2 - 100, height - barHeight + 20, 200, 4);
            g.setColor(new Color(200, 100, 30));
            g.fillRect(width/2 - 100, height - barHeight + 20, (int)(200 * progress), 4);
            
            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            g.setColor(new Color(180, 180, 200, 180));
            g.drawString("Frame " + (frameIndex + 1) + " / " + frames.size(), width/2 - 30, height - barHeight + 14);
            g.setColor(new Color(140, 140, 160, 180));
            g.drawString("ENTER to skip", width - 120, height - barHeight + 35);
        } else {
            drawBuiltinCutscene(g, width, height, lab, barHeight);
        }
    }
    
    private void drawBuiltinCutscene(Graphics2D g, int w, int h, LabBackground lab, int bar) {
        g.drawImage(lab.get(w, h), 0, 0, null);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, w, h);
        
        String[][] scenes = {
            {"SECTOR A7 — CONTAINMENT BREACH", "An unknown subject has been detected."},
            {"ALERT: HOSTILE ENTITY IDENTIFIED", "Designation: UNKNOWN. Threat Level: EXTREME."},
            {"SUBJECT ZERO", "Last surviving test subject. 1000HP. Highly volatile."},
            {"THREE PHASES OF TERROR", "It evolves as its HP drops. Adapt or die."},
            {"FREEDOM IS NOT A SYMPTOM.", "It is a choice."},
            {"— ROUND 1 —", "Press J to attack. Spam J for combos. P to pause."}
        };
        
        int sceneIndex = Math.min(builtinFrame, scenes.length - 1);
        float fade = Math.min(1f, builtinTick / 20f);
        
        // Title text
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 32));
        FontMetrics fm = g.getFontMetrics();
        int textX = (w - fm.stringWidth(scenes[sceneIndex][0])) / 2;
        g.setColor(new Color(0, 0, 0, (int)(180 * fade)));
        g.drawString(scenes[sceneIndex][0], textX + 2, h/2 - 20 + 2);
        g.setColor(new Color(235, 220, 190, (int)(255 * fade)));
        g.drawString(scenes[sceneIndex][0], textX, h/2 - 20);
        
        // Subtitle
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        int subX = (w - fm.stringWidth(scenes[sceneIndex][1])) / 2;
        g.setColor(new Color(160, 170, 200, (int)(220 * fade)));
        g.drawString(scenes[sceneIndex][1], subX, h/2 + 16);
        
        // Silhouettes
        drawSilhouette(g, 120, h - bar - 10, true, fade);
        drawSilhouette(g, w - 120, h - bar - 10, false, fade);
        
        // Scanning line effect
        if (builtinTick < 60) {
            float linePos = (builtinTick / 60f) * (h - bar * 2) + bar;
            g.setColor(new Color(100, 200, 255, 40));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(0, (int)linePos, w, (int)linePos);
        }
        
        // Progress bar
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, bar);
        g.fillRect(0, h - bar, w, bar);
        float progress = (builtinFrame * BUILTIN_TICKS + builtinTick) / (float)(BUILTIN_TOTAL * BUILTIN_TICKS);
        g.setColor(new Color(60, 60, 80));
        g.fillRect(w/2 - 100, h - bar + 20, 200, 4);
        g.setColor(new Color(200, 100, 30));
        g.fillRect(w/2 - 100, h - bar + 20, (int)(200 * progress), 4);
        
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(140, 140, 160, 180));
        g.drawString("ENTER to skip", w - 120, h - bar + 35);
        g.setColor(new Color(120, 140, 180, 150));
        g.drawString("No video loaded — using built-in cutscene", 20, h - bar + 35);
    }
    
    private void drawSilhouette(Graphics2D g, int cx, int cy, boolean isPlayer, float alpha) {
        Color bodyColor = isPlayer ? new Color(210, 55, 55, (int)(200 * alpha)) : new Color(70, 90, 130, (int)(200 * alpha));
        Color limbColor = isPlayer ? new Color(170, 30, 30, (int)(200 * alpha)) : new Color(50, 70, 110, (int)(200 * alpha));
        int headY = cy - 95;
        int bodyTop = headY + 24;
        int bodyBottom = cy - 22;
        int midArm = bodyTop + 18;
        
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(limbColor);
        g.drawLine(cx, bodyBottom, cx + 16, cy - 2);
        g.drawLine(cx, bodyBottom, cx - 16, cy - 2);
        
        g.setColor(bodyColor);
        g.drawLine(cx, bodyTop, cx, bodyBottom);
        
        g.setColor(limbColor);
        g.drawLine(cx, midArm, cx + 22, midArm + 18);
        g.drawLine(cx, midArm, cx - 22, midArm + 18);
        
        g.setColor(bodyColor);
        g.fillOval(cx - 12, headY, 24, 24);
        
        if (isPlayer) {
            g.setColor(new Color(210, 55, 55, (int)(60 * alpha)));
            g.fillOval(cx - 20, headY - 8, 40, 40);
        }
    }
}

// =====================================================================
//  MAIN GAME PANEL — Core Game Logic and UI
// =====================================================================

public class ProjectAdrenaline extends JPanel implements ActionListener, KeyListener {
    
    // Screen states
    enum Screen { MENU, OPTIONS, CUTSCENE, PLAYING, PAUSED, RESULT }
    Screen screen = Screen.MENU;
    
    // Game objects
    Player player;
    Enemy enemy;
    List<Character> characters = new ArrayList<>();
    EffectManager effects = new EffectManager();
    LabBackground labBackground = new LabBackground();
    VideoCutscene cutscene = new VideoCutscene();
    
    // Pause menu
    int pauseSelection = 0;
    static final String[] PAUSE_ITEMS = {"Resume", "Restart", "Load Video", "Options", "Main Menu"};
    
    // Game state
    String resultMessage = "";
    int resultTimer = 0;
    int screenShake = 0;
    boolean menuBlink = true;
    int blinkTimer = 0;
    boolean leftPressed, rightPressed, jumpPressed;
    Timer gameTimer;
    
    // Constants
    static final int WINDOW_WIDTH = 800, WINDOW_HEIGHT = 520, FLOOR_Y = 420;
    
    public ProjectAdrenaline() {
        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        setBackground(new Color(10, 12, 22));
        setFocusable(true);
        addKeyListener(this);
        gameTimer = new Timer(16, this);
        gameTimer.start();
    }
    
    /** Initialize new game instance */
    void initGame() {
        player = new Player(160, FLOOR_Y, WINDOW_WIDTH);
        enemy = new Enemy(600, FLOOR_Y, WINDOW_WIDTH);
        characters.clear();
        characters.add(player);
        characters.add(enemy);
        effects = new EffectManager();
        resultMessage = "";
        resultTimer = 0;
        screenShake = 0;
        leftPressed = false;
        rightPressed = false;
        jumpPressed = false;
    }
    
    void startCutscene() {
        cutscene.reset();
        screen = Screen.CUTSCENE;
        SoundEngine.play(12);
    }
    
    /** Open file chooser to load custom video frames */
    void openVideoChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Load Cutscene — Select a FOLDER of image frames OR a single image");
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new FileNameExtensionFilter("Images / Folders", "png", "jpg", "jpeg", "bmp", "gif"));
        
        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            boolean ok = false;
            if (selected.isDirectory()) {
                ok = cutscene.loadFolder(selected);
            } else {
                ok = cutscene.loadFile(selected);
            }
            if (!ok) {
                JOptionPane.showMessageDialog(this, "Could not load frames from that location.\nMake sure it's a folder with PNG/JPG images named in order.", 
                    "Load Failed", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Loaded " + cutscene.frames.size() + " frames!", "Video Loaded", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        requestFocusInWindow();
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        blinkTimer++;
        if (blinkTimer > 40) {
            menuBlink = !menuBlink;
            blinkTimer = 0;
        }
        
        switch (screen) {
            case CUTSCENE:
                cutscene.update();
                if (cutscene.done) {
                    initGame();
                    screen = Screen.PLAYING;
                }
                break;
            case PLAYING:
                gameLoop();
                break;
            default:
                break;
        }
        repaint();
    }
    
    /** Main game update loop */
    void gameLoop() {
        // Player movement input
        if (player.isAlive()) {
            if (leftPressed && !player.attacking) {
                player.velX = -6.5;
                player.facingRight = false;
                if (player.state == Player.State.IDLE) player.state = Player.State.WALK;
            } else if (rightPressed && !player.attacking) {
                player.velX = 6.5;
                player.facingRight = true;
                if (player.state == Player.State.IDLE) player.state = Player.State.WALK;
            } else {
                if (!player.attacking) {
                    player.velX = 0;
                    if (player.state == Player.State.WALK) player.state = Player.State.IDLE;
                }
            }
            
            if (jumpPressed && player.y >= player.groundY && !player.attacking) {
                player.velY = -12.5;
                player.state = Player.State.JUMP;
                jumpPressed = false;
                SoundEngine.play(7);
            }
        }
        
        // Update game logic
        int prevEnemyHealth = enemy.health;
        int prevPlayerHealth = player.health;
        
        player.checkHit(enemy, effects);
        for (Character c : characters) c.update(characters, effects);
        effects.update();
        
        // Screen shake on damage
        int enemyDamage = prevEnemyHealth - enemy.health;
        if (enemyDamage > 0) {
            screenShake = Math.max(screenShake, enemyDamage >= 20 ? 12 : 7);
            SoundEngine.play(6);
        }
        int playerDamage = prevPlayerHealth - player.health;
        if (playerDamage > 0) {
            screenShake = Math.max(screenShake, playerDamage >= 14 ? 10 : 5);
        }
        if (screenShake > 0) screenShake--;
        
        // Check for victory/defeat
        if (resultMessage.isEmpty()) {
            if (!enemy.isAlive()) {
                resultMessage = "VICTORY!";
                resultTimer = 180;
                SoundEngine.play(8);
            } else if (!player.isAlive()) {
                resultMessage = "DEFEATED...";
                resultTimer = 180;
                SoundEngine.play(9);
            }
        }
        
        if (resultTimer > 0) {
            resultTimer--;
            if (resultTimer == 0) screen = Screen.RESULT;
        }
    }
    
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D)g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        switch (screen) {
            case MENU: drawMenu(g); break;
            case OPTIONS: drawOptions(g); break;
            case CUTSCENE: cutscene.draw(g, WINDOW_WIDTH, WINDOW_HEIGHT, labBackground); break;
            case PLAYING: drawGame(g); break;
            case PAUSED: drawGame(g); drawPauseMenu(g); break;
            case RESULT: drawResult(g); break;
        }
        g.dispose();
    }
    
    // ========== UI DRAWING METHODS ==========
    
    void drawMenu(Graphics2D g) {
        g.drawImage(labBackground.get(WINDOW_WIDTH, WINDOW_HEIGHT), 0, 0, null);
        
        // Scanline effect
        g.setColor(new Color(0, 0, 0, 65));
        for (int y = 0; y < WINDOW_HEIGHT; y += 3) g.drawLine(0, y, WINDOW_WIDTH, y);
        
        // Decorative border
        for (int i = 0; i < 60; i++) {
            g.setColor(new Color(0, 0, 0, Math.min(255, (int)(i * 1.8))));
            g.drawRect(i, i, WINDOW_WIDTH - i*2, WINDOW_HEIGHT - i*2);
        }
        
        // Title
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 52));
        g.setColor(new Color(200, 120, 30, 65));
        g.drawString("Project Adrenaline", 60, 321);
        g.setColor(new Color(235, 220, 190));
        g.drawString("Project Adrenaline", 57, 318);
        
        // Menu buttons
        int bx = 70, by = 330, bw = 175, bh = 38, gap = 12;
        drawMenuButton(g, "▶  Play", bx, by, bw, bh, Color.WHITE);
        drawMenuButton(g, "⚙  Options", bx, by + bh + gap, bw, bh, Color.WHITE);
        drawMenuButton(g, "📁  Load Video", bx, by + (bh + gap) * 2, bw, bh, new Color(180, 220, 255));
        drawMenuButton(g, "✕  Exit", bx, by + (bh + gap) * 3, bw, bh, new Color(255, 160, 160));
        
        // Blinking demo text
        if (menuBlink) {
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(new Color(200, 100, 30, 200));
            g.drawString("—  DEMO VERSION — PROJECT ADRENALINE —", 220, WINDOW_HEIGHT - 12);
        }
        
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(120, 140, 180, 180));
        g.drawString("P TO PLAY / O OPTIONS / V LOAD VIDEO / ESC EXIT", 155, WINDOW_HEIGHT - 28);
    }
    
    void drawOptions(Graphics2D g) {
        g.drawImage(labBackground.get(WINDOW_WIDTH, WINDOW_HEIGHT), 0, 0, null);
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 38));
        g.setColor(new Color(220, 200, 160));
        g.drawString("Options", 55, 90);
        
        g.setColor(new Color(200, 100, 30));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(55, 100, 420, 100);
        
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(new Color(180, 180, 200));
        g.drawString("CONTROLS", 55, 132);
        
        String[] keys = {"A / D", "SPACE", "J spam!", "K", "P or ESC"};
        String[] actions = {"Move left / right (stops instantly)", "Jump", "JAB → CROSS → UPPERCUT → SPIN SLASH", "Switch weapon", "Pause game"};
        
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        for (int i = 0; i < keys.length; i++) {
            g.setColor(new Color(230, 190, 80));
            g.drawString(keys[i], 65, 160 + i * 30);
            g.setColor(new Color(180, 200, 220));
            g.drawString("—  " + actions[i], 200, 160 + i * 30);
        }
        
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        String[] tips = {
            "BOSS has 1000 HP and 3 phases — get ready.",
            "Phase 2 (66% HP): faster, more damage, dashes.",
            "Phase 3 (33% HP): RAGE multi-hit, jumping constantly.",
            "Video cutscene: load a FOLDER of PNG/JPG frames.",
            "Frames should be named 0001.png, 0002.png … in order."
        };
        for (int i = 0; i < tips.length; i++) {
            g.setColor(i < 1 ? new Color(255, 100, 100) : i < 3 ? new Color(255, 160, 60) : new Color(120, 180, 255));
            g.drawString("• " + tips[i], 65, 320 + i * 20);
        }
        
        drawMenuButton(g, "←  Back", 65, 445, 140, 38, new Color(200, 200, 220));
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(100, 100, 140));
        g.drawString("PRESS O TO RETURN", 65, WINDOW_HEIGHT - 12);
    }
    
    void drawGame(Graphics2D g) {
        // Apply screen shake
        if (screenShake > 0) {
            g.translate((int)((Math.random() - 0.5) * screenShake * 2), 
                       (int)((Math.random() - 0.5) * screenShake * 2));
        }
        
        g.drawImage(labBackground.get(WINDOW_WIDTH, WINDOW_HEIGHT), 0, 0, null);
        
        // Draw shadows
        for (Character c : characters) {
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval((int)c.x - 22, FLOOR_Y - 9, 44, 13);
        }
        
        effects.draw(g, WINDOW_WIDTH, WINDOW_HEIGHT);
        for (Character c : characters) c.draw(g);
        drawHUD(g);
        
        // Victory/defeat overlay
        if (!resultMessage.isEmpty() && resultTimer > 0) {
            float alpha = Math.min(1f, (180 - resultTimer) / 15f);
            g.setColor(new Color(0, 0, 0, (int)(160 * alpha)));
            g.fillRect(0, 185, WINDOW_WIDTH, 95);
            g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 60));
            boolean win = resultMessage.startsWith("V");
            g.setColor(new Color(win ? 0.2f : 0.9f, win ? 0.9f : 0.2f, 0.2f, alpha));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(resultMessage, (WINDOW_WIDTH - fm.stringWidth(resultMessage)) / 2, 250);
        }
        
        // Phase indicator
        if (enemy != null && enemy.isAlive()) {
            String phaseText = enemy.phase == 3 ? "⚠ PHASE 3: RAGE" : enemy.phase == 2 ? "⚠ PHASE 2" : null;
            if (phaseText != null) {
                g.setFont(new Font("Monospaced", Font.BOLD, 11));
                g.setColor(enemy.phase == 3 ? new Color(255, 40, 40, 200) : new Color(255, 160, 40, 200));
                g.drawString(phaseText, WINDOW_WIDTH - 180, 42);
            }
        }
        
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(200, 100, 30, 120));
        g.drawString("DEMO — PROJECT ADRENALINE", WINDOW_WIDTH - 215, WINDOW_HEIGHT - 8);
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(100, 120, 160, 160));
        g.drawString("P = Pause", WINDOW_WIDTH/2 - 22, WINDOW_HEIGHT - 8);
    }
    
    void drawPauseMenu(Graphics2D g) {
        // Frosted overlay
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        
        int panelWidth = 300, panelHeight = 320;
        int panelX = (WINDOW_WIDTH - panelWidth) / 2;
        int panelY = (WINDOW_HEIGHT - panelHeight) / 2;
        
        // Panel background
        g.setColor(new Color(15, 18, 32, 230));
        g.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 18, 18);
        g.setColor(new Color(200, 160, 60, 180));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 18, 18);
        
        // Title
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 32));
        g.setColor(new Color(235, 220, 190));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("PAUSED", (WINDOW_WIDTH - fm.stringWidth("PAUSED")) / 2, panelY + 48);
        
        g.setColor(new Color(150, 120, 50, 180));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(panelX + 20, panelY + 58, panelX + panelWidth - 20, panelY + 58);
        
        // Menu items
        int itemHeight = 42, startY = panelY + 78;
        for (int i = 0; i < PAUSE_ITEMS.length; i++) {
            boolean selected = (i == pauseSelection);
            if (selected) {
                g.setColor(new Color(200, 160, 60, 60));
                g.fillRoundRect(panelX + 14, startY + i * itemHeight - 2, panelWidth - 28, itemHeight - 4, 10, 10);
            }
            g.setFont(new Font("Monospaced", Font.BOLD, 15));
            g.setColor(selected ? new Color(255, 220, 80) : new Color(180, 190, 210));
            String item = (selected ? "▶  " : "   ") + PAUSE_ITEMS[i];
            fm = g.getFontMetrics();
            g.drawString(item, (WINDOW_WIDTH - fm.stringWidth(item)) / 2, startY + i * itemHeight + 28);
        }
        
        // Footer
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(120, 130, 160, 180));
        g.drawString("↑↓ Navigate   ENTER Select   P Resume", (WINDOW_WIDTH - 250) / 2, panelY + panelHeight - 14);
    }
    
    void drawHUD(Graphics2D g) {
        drawBigBar(g, 20, 12, 200, 16, player.health, player.maxHealth, "PLAYER", new Color(50, 200, 80));
        drawBigBar(g, WINDOW_WIDTH - 220, 12, 200, 16, enemy.health, enemy.maxHealth, 
                   "BOSS [" + enemy.health + " / " + enemy.maxHealth + "]", new Color(200, 60, 60));
        
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.setColor(new Color(180, 200, 240, 200));
        String weaponText = "[ " + player.weapon.getName().toUpperCase() + " ]  K to switch";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(weaponText, 20, 38);
    }
    
    void drawBigBar(Graphics2D g, int x, int y, int barWidth, int barHeight, int currentHp, int maxHp, String label, Color fillColor) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(x - 2, y - 2, barWidth + 4, barHeight + 18);
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(180, 190, 210));
        g.drawString(label, x, y + barHeight + 13);
        g.setColor(new Color(50, 0, 0));
        g.fillRect(x, y, barWidth, barHeight);
        float percent = currentHp / (float)maxHp;
        g.setColor(fillColor);
        g.fillRect(x, y, (int)(barWidth * percent), barHeight);
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(x, y, (int)(barWidth * percent), barHeight / 2);
        g.setColor(new Color(200, 200, 200, 80));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, barWidth, barHeight);
    }
    
    void drawResult(Graphics2D g) {
        g.drawImage(labBackground.get(WINDOW_WIDTH, WINDOW_HEIGHT), 0, 0, null);
        g.setColor(new Color(0, 0, 0, 185));
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        
        boolean win = enemy != null && !enemy.isAlive();
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 68));
        g.setColor(win ? new Color(60, 230, 100) : new Color(230, 60, 60));
        String msg = win ? "VICTORY!" : "DEFEATED...";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (WINDOW_WIDTH - fm.stringWidth(msg)) / 2, 220);
        
        if (win && player != null) {
            g.setFont(new Font("Monospaced", Font.BOLD, 15));
            g.setColor(new Color(200, 190, 120));
            String sub = "Max combo: " + player.comboCount + "   Boss neutralised.";
            fm = g.getFontMetrics();
            g.drawString(sub, (WINDOW_WIDTH - fm.stringWidth(sub)) / 2, 265);
        }
        
        drawMenuButton(g, "▶  Play Again", (WINDOW_WIDTH - 180) / 2, 320, 180, 44, Color.WHITE);
        drawMenuButton(g, "←  Main Menu", (WINDOW_WIDTH - 180) / 2, 376, 180, 44, new Color(200, 200, 240));
        
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(100, 100, 140));
        g.drawString("R RETRY   M MENU", (WINDOW_WIDTH - 130) / 2, WINDOW_HEIGHT - 12);
    }
    
    void drawMenuButton(Graphics2D g, String label, int x, int y, int width, int height, Color color) {
        g.setColor(new Color(200, 180, 120, 35));
        g.fillRect(x, y, width, height);
        g.setColor(new Color(200, 180, 120, 90));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, width, height);
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.setColor(color);
        g.drawString(label, x + 16, y + height - 11);
    }
    
    void executePauseItem() {
        SoundEngine.play(11);
        switch (pauseSelection) {
            case 0: screen = Screen.PLAYING; break;
            case 1: initGame(); screen = Screen.PLAYING; break;
            case 2: openVideoChooser(); break;
            case 3: screen = Screen.OPTIONS; break;
            case 4: screen = Screen.MENU; break;
        }
    }
    
    // ========== KEYBOARD INPUT HANDLING ==========
    
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        
        switch (screen) {
            case MENU:
                if (code == KeyEvent.VK_P || code == KeyEvent.VK_ENTER) {
                    startCutscene();
                    SoundEngine.play(11);
                }
                if (code == KeyEvent.VK_O) {
                    screen = Screen.OPTIONS;
                    SoundEngine.play(11);
                }
                if (code == KeyEvent.VK_V) openVideoChooser();
                if (code == KeyEvent.VK_ESCAPE) System.exit(0);
                break;
                
            case OPTIONS:
                if (code == KeyEvent.VK_O || code == KeyEvent.VK_ESCAPE) {
                    screen = Screen.MENU;
                    SoundEngine.play(11);
                }
                break;
                
            case CUTSCENE:
                if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_ESCAPE) cutscene.skip();
                break;
                
            case PLAYING:
                if (code == KeyEvent.VK_A) leftPressed = true;
                if (code == KeyEvent.VK_D) rightPressed = true;
                if (code == KeyEvent.VK_SPACE) jumpPressed = true;
                if (code == KeyEvent.VK_J) player.triggerAttack();
                if (code == KeyEvent.VK_K) {
                    player.setWeapon(player.weapon instanceof Fists ? new Sword() : new Fists());
                    SoundEngine.play(11);
                }
                if (code == KeyEvent.VK_P || code == KeyEvent.VK_ESCAPE) {
                    screen = Screen.PAUSED;
                    pauseSelection = 0;
                    leftPressed = false;
                    rightPressed = false;
                    jumpPressed = false;
                }
                break;
                
            case PAUSED:
                if (code == KeyEvent.VK_UP) {
                    pauseSelection = (pauseSelection - 1 + PAUSE_ITEMS.length) % PAUSE_ITEMS.length;
                    SoundEngine.play(11);
                }
                if (code == KeyEvent.VK_DOWN) {
                    pauseSelection = (pauseSelection + 1) % PAUSE_ITEMS.length;
                    SoundEngine.play(11);
                }
                if (code == KeyEvent.VK_ENTER) executePauseItem();
                if (code == KeyEvent.VK_P || code == KeyEvent.VK_ESCAPE) screen = Screen.PLAYING;
                break;
                
            case RESULT:
                if (code == KeyEvent.VK_R) startCutscene();
                if (code == KeyEvent.VK_M || code == KeyEvent.VK_ESCAPE) screen = Screen.MENU;
                break;
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A) leftPressed = false;
        if (code == KeyEvent.VK_D) rightPressed = false;
        if (code == KeyEvent.VK_SPACE) jumpPressed = false;
    }
    
    @Override
    public void keyTyped(KeyEvent e) {}
    
    // Mouse click handler for menu buttons
    {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int mx = e.getX(), my = e.getY();
                
                if (screen == Screen.MENU) {
                    int bx = 70, by = 330, bw = 175, bh = 38, gap = 12;
                    if (isHit(mx, my, bx, by, bw, bh)) {
                        startCutscene();
                        SoundEngine.play(11);
                    }
                    if (isHit(mx, my, bx, by + bh + gap, bw, bh)) {
                        screen = Screen.OPTIONS;
                        SoundEngine.play(11);
                    }
                    if (isHit(mx, my, bx, by + (bh + gap) * 2, bw, bh)) openVideoChooser();
                    if (isHit(mx, my, bx, by + (bh + gap) * 3, bw, bh)) System.exit(0);
                } else if (screen == Screen.OPTIONS) {
                    if (isHit(mx, my, 65, 445, 140, 38)) {
                        screen = Screen.MENU;
                        SoundEngine.play(11);
                    }
                } else if (screen == Screen.RESULT) {
                    int bxm = (WINDOW_WIDTH - 180) / 2;
                    if (isHit(mx, my, bxm, 320, 180, 44)) startCutscene();
                    if (isHit(mx, my, bxm, 376, 180, 44)) screen = Screen.MENU;
                } else if (screen == Screen.PAUSED) {
                    int panelWidth = 300, panelHeight = 320;
                    int panelX = (WINDOW_WIDTH - panelWidth) / 2;
                    int startY = (WINDOW_HEIGHT - panelHeight) / 2 + 78;
                    int itemHeight = 42;
                    for (int i = 0; i < PAUSE_ITEMS.length; i++) {
                        if (isHit(mx, my, panelX + 14, startY + i * itemHeight - 2, panelWidth - 28, itemHeight - 4)) {
                            pauseSelection = i;
                            executePauseItem();
                        }
                    }
                }
                requestFocusInWindow();
            }
        });
    }
    
    boolean isHit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Project Adrenaline [DEMO] — 1000HP BOSS");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            ProjectAdrenaline game = new ProjectAdrenaline();
            frame.add(game);
            frame.pack();
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
