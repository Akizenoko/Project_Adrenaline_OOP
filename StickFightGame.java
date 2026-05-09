import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

// =====================================================================
//  PROJECT ADRENALINE  —  Demo Build
//  Pure Java / Swing — no external libraries needed
//  Compile:  javac StickFightGame.java
//  Run:      java  StickFightGame
// =====================================================================

// ----- Weapon interface -----
interface Weapon {
    int    getBaseDamage();
    double getCritChance();
    int    getRange();
    String getName();
}

class Fists implements Weapon {
    public int    getBaseDamage() { return 8;    }
    public double getCritChance() { return 0.15; }
    public int    getRange()      { return 32;   }
    public String getName()       { return "Fists"; }
}

class Sword implements Weapon {
    public int    getBaseDamage() { return 18;   }
    public double getCritChance() { return 0.28; }
    public int    getRange()      { return 58;   }
    public String getName()       { return "Sword"; }
}

// =====================================================================
//  Abstract Character
// =====================================================================
abstract class Character {
    double x, y;
    double velX = 0, velY = 0;
    int health, maxHealth;
    final int W = 40, H = 100;
    int groundY;
    boolean facingRight = true;
    // knockback
    double knockX = 0;
    int    knockTimer = 0;
    // hurt flash
    int hurtFlash = 0;

    Character(int x, int y, int hp) {
        this.x = x; this.y = y;
        maxHealth = hp; health = hp;
        groundY = y;
    }

    abstract void draw(Graphics2D g);
    abstract void update(List<Character> others);
    Rectangle getHitbox() {
        return new Rectangle((int)x - W/2, (int)y - H, W, H);
    }
    void takeDamage(int dmg) {
        health = Math.max(0, health - dmg);
        hurtFlash = 12;
    }
    void applyKnockback(double kx) {
        knockX = kx; knockTimer = 10;
    }
    boolean isAlive() { return health > 0; }

    /** shared gravity + knockback physics */
    void physicsStep(int leftBound, int rightBound) {
        // horizontal knockback decays
        if (knockTimer > 0) {
            x += knockX;
            knockX *= 0.75;
            knockTimer--;
        }
        x += velX;
        y += velY;
        velY += 0.65;                // gravity
        if (y >= groundY) { y = groundY; velY = 0; }
        x = Math.max(leftBound, Math.min(rightBound, x));
        if (hurtFlash > 0) hurtFlash--;
    }

    /** Draw a health bar above the character */
    void drawHealthBar(Graphics2D g, int barW, int barH) {
        int bx = (int)x - barW/2;
        int by = (int)y - H - barH - 6;
        g.setColor(new Color(60,0,0));
        g.fillRect(bx, by, barW, barH);
        float pct = health / (float) maxHealth;
        Color fill = pct > 0.5f ? new Color(40,200,60)
                   : pct > 0.25f ? new Color(230,180,0)
                   : new Color(220,40,40);
        g.setColor(fill);
        g.fillRect(bx, by, (int)(barW * pct), barH);
        g.setColor(new Color(180,180,180,80));
        g.drawRect(bx, by, barW, barH);
    }
}

// =====================================================================
//  Player
// =====================================================================
class Player extends Character {

    enum State { IDLE, WALK, JUMP, ATTACK }

    State   state        = State.IDLE;
    Weapon  weapon       = new Fists();

    // attack
    int attackFrame    = 0;
    int attackCooldown = 0;
    boolean hitLanded  = false;

    // combo / crit
    int    comboCount  = 0;
    int    comboTimer  = 0;
    String critText    = "";
    int    critTimer   = 0;

    // sprite animation
    int animTick = 0, animFrame = 0;

    // reference to panel for bounds
    int panelW;

    Player(int x, int y, int panelW) {
        super(x, y, 100);
        this.panelW = panelW;
    }

    void setWeapon(Weapon w) { weapon = w; }

    // ---- attack ----
    void startAttack() {
        if (attackCooldown > 0 || state == State.ATTACK) return;
        state = State.ATTACK;
        attackFrame = 0;
        hitLanded   = false;
        animFrame   = 0;
        animTick    = 0;
    }

    Rectangle getAttackBox() {
        int r = weapon.getRange(), ah = 32;
        int ay = (int)y - H + 15;
        return facingRight
            ? new Rectangle((int)x + 14, ay, r, ah)
            : new Rectangle((int)x - 14 - r, ay, r, ah);
    }

    void checkHit(Character target) {
        if (!hitLanded && state == State.ATTACK
                && attackFrame >= 5 && attackFrame <= 12) {
            if (getAttackBox().intersects(target.getHitbox())) {
                hitLanded = true;
                boolean crit = Math.random() < weapon.getCritChance();
                int dmg = crit ? weapon.getBaseDamage() * 2 : weapon.getBaseDamage();
                target.takeDamage(dmg);
                double kb = facingRight ? 9 : -9;
                target.applyKnockback(kb);
                comboCount++; comboTimer = 90;
                if (crit) { critText = "CRITICAL!"; critTimer = 35; }
            }
        }
    }

    @Override
    void update(List<Character> others) {
        if (attackCooldown > 0) attackCooldown--;
        if (comboTimer   > 0) comboTimer--;
        else comboCount = 0;
        if (critTimer > 0) critTimer--;

        if (state == State.ATTACK) {
            attackFrame++;
            velX *= 0.9;
            if (attackFrame >= 20) {
                state = State.IDLE;
                attackFrame   = 0;
                hitLanded     = false;
                attackCooldown = 28;
            }
        }

        // animation tick
        animTick++;
        int fps = (state == State.WALK) ? 5 : 8;
        if (animTick >= fps) {
            animTick = 0;
            int frames = (state == State.WALK) ? 4 : 1;
            animFrame = (animFrame + 1) % frames;
        }

        physicsStep(32, panelW - 32);
    }

    @Override
    void draw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // flip if facing left
        if (!facingRight) {
            g2.translate((int)x * 2, 0);
            g2.scale(-1, 1);
        }
        int cx = (int)x, cy = (int)y;

        // hurt tint
        if (hurtFlash > 0 && hurtFlash % 4 < 2) {
            g2.setColor(new Color(255, 80, 80, 180));
            g2.fillRect(cx - W/2 - 2, cy - H - 2, W + 4, H + 4);
        }

        drawStickman(g2, cx, cy, state, attackFrame, animFrame, weapon, true);
        g2.dispose();

        // HUD
        drawHealthBar(g, 44, 5);

        // combo text
        int tx = (int)x;
        if (comboCount > 1 && comboTimer > 0) {
            g.setFont(new Font("Arial", Font.BOLD, 15));
            g.setColor(new Color(80, 160, 255));
            g.drawString("COMBO x" + comboCount, tx - 32, (int)y - H - 20);
        }
        if (critTimer > 0) {
            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.setColor(new Color(255, 200, 30));
            g.drawString(critText, tx - 34, (int)y - H - 36);
        }

        // weapon label (small)
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(200, 200, 200, 160));
        g.drawString("[" + weapon.getName() + "]", (int)x - 18, (int)y - H - 10);
    }

    /** shared stickman drawing — used by Player and Enemy */
    static void drawStickman(Graphics2D g, int cx, int cy,
                              Object stateObj, int atkFrame, int animFr,
                              Weapon weapon, boolean isPlayer) {

        boolean isAttack = stateObj.toString().contains("ATTACK");
        boolean isWalk   = stateObj.toString().contains("WALK");
        boolean isJump   = stateObj.toString().contains("JUMP");

        // walk cycle angles
        float legSwing  = isWalk ? (float) Math.sin(animFr * Math.PI / 2) * 28 : 0;
        float armSwing  = isWalk ? (float)-Math.sin(animFr * Math.PI / 2) * 20 : 0;

        Color bodyCol  = isPlayer ? new Color(210, 55, 55)  : new Color(70, 90, 130);
        Color limbCol  = isPlayer ? new Color(170, 30, 30)  : new Color(50, 70, 110);
        Color headCol  = isPlayer ? new Color(220, 70, 70)  : new Color(85, 105, 145);

        int headY  = cy - 100;
        int bodyT  = headY + 24;
        int bodyB  = cy - 22;
        int midArm = bodyT + 18;

        // ---- LEGS ----
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(limbCol);
        if (isJump) {
            // both legs angled back
            g.drawLine(cx, bodyB, cx + 18, bodyB + 22);
            g.drawLine(cx, bodyB, cx - 18, bodyB + 22);
        } else if (isAttack) {
            // planted stance
            g.drawLine(cx, bodyB, cx + 16, cy - 2);
            g.drawLine(cx, bodyB, cx - 16, cy - 2);
        } else {
            // walk / idle
            double lr = Math.toRadians(legSwing);
            int lx1 = cx + (int)(22 * Math.sin(lr));
            int lx2 = cx - (int)(22 * Math.sin(lr));
            g.drawLine(cx, bodyB, lx1, cy - 2);
            g.drawLine(cx, bodyB, lx2, cy - 2);
        }

        // ---- BODY ----
        g.setColor(bodyCol);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx, bodyT, cx, bodyB);

        // ---- ARMS ----
        g.setColor(limbCol);
        if (isAttack) {
            // punch animation: one arm extends, lerped by attackFrame
            float ext = Math.min(1f, atkFrame / 8f);
            if (ext > 0.5f) ext = 1f - (atkFrame - 8) / 8f;
            ext = Math.max(0, ext);
            int punchX = cx + (int)(weapon.getRange() * 0.85 * ext);
            int punchY = midArm - (int)(10 * ext);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(cx, midArm, punchX, punchY);   // punching arm
            g.drawLine(cx, midArm, cx - 22, midArm + 14); // guard arm

            // weapon extension
            if (weapon instanceof Sword) {
                g.setColor(new Color(200, 220, 255));
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int sx = punchX + (int)(22 * ext);
                g.drawLine(punchX, punchY, sx, punchY - 8);
                g.fillOval(sx - 4, punchY - 12, 8, 8);
            } else {
                g.setColor(new Color(230, 190, 130));
                g.fillOval(punchX - 7, punchY - 7, 14, 14);
            }
        } else {
            double ar = Math.toRadians(armSwing);
            int ax1 = cx + (int)(26 * Math.sin(ar));
            int ay1 = midArm + 18 + (int)(6 * Math.cos(ar));
            int ax2 = cx - (int)(26 * Math.sin(ar));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(cx, midArm, ax1, ay1);
            g.drawLine(cx, midArm, ax2, ay1);
            // idle weapon
            if (weapon instanceof Sword) {
                g.setColor(new Color(190, 210, 240, 180));
                g.setStroke(new BasicStroke(3f));
                g.drawLine(ax1, ay1, ax1 + 18, ay1 - 8);
            }
        }

        // ---- HEAD ----
        g.setColor(headCol);
        g.setStroke(new BasicStroke(2f));
        g.fillOval(cx - 13, headY, 26, 26);

        // eyes
        g.setColor(isPlayer ? Color.BLACK : new Color(200, 50, 50));
        g.fillOval(cx - 6, headY + 7, 5, 5);
        g.fillOval(cx + 1, headY + 7, 5, 5);

        if (!isPlayer) {
            // angry brows
            g.setColor(new Color(180, 30, 30));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(cx - 8, headY + 4, cx - 3, headY + 7);
            g.drawLine(cx + 8, headY + 4, cx + 3, headY + 7);
        } else {
            // white pupils
            g.setColor(Color.WHITE);
            g.fillOval(cx - 5, headY + 8, 2, 2);
            g.fillOval(cx + 2, headY + 8, 2, 2);
        }

        // scarf / cape for player
        if (isPlayer) {
            g.setColor(new Color(180, 30, 30, 180));
            int[] sx = { cx, cx - 6, cx - 14, cx - 4 };
            int[] sy = { bodyT, bodyT + 12, bodyT + 26, bodyT + 8 };
            g.fillPolygon(sx, sy, 4);
        }
    }
}

// =====================================================================
//  Enemy  —  simple AI: chase, jump, attack
// =====================================================================
class Enemy extends Character {

    enum State { IDLE, WALK, JUMP, ATTACK }

    State state = State.IDLE;
    int attackFrame    = 0;
    int attackCooldown = 0;
    boolean hitLanded  = false;
    int animTick = 0, animFrame = 0;

    int panelW;

    // AI timers
    int thinkTimer  = 40;
    int jumpCooldown = 0;

    Enemy(int x, int y, int panelW) {
        super(x, y, 80);
        this.panelW = panelW;
        facingRight = false;
    }

    Rectangle getAttackBox() {
        int r = 36, ah = 32;
        int ay = (int)y - H + 15;
        return facingRight
            ? new Rectangle((int)x + 14, ay, r, ah)
            : new Rectangle((int)x - 14 - r, ay, r, ah);
    }

    void checkHit(Character target) {
        if (!hitLanded && state == State.ATTACK
                && attackFrame >= 5 && attackFrame <= 12) {
            if (getAttackBox().intersects(target.getHitbox())) {
                hitLanded = true;
                int dmg = 8 + (int)(Math.random() * 6);
                target.takeDamage(dmg);
                double kb = facingRight ? 7 : -7;
                target.applyKnockback(kb);
            }
        }
    }

    @Override
    void update(List<Character> others) {
        if (attackCooldown > 0) attackCooldown--;
        if (jumpCooldown   > 0) jumpCooldown--;

        // find player
        Character player = others.stream()
            .filter(c -> c instanceof Player).findFirst().orElse(null);

        if (player != null && isAlive() && player.isAlive()) {
            double dx = player.x - x;
            facingRight = dx > 0;
            double dist = Math.abs(dx);

            // --- AI state machine ---
            if (state == State.ATTACK) {
                attackFrame++;
                velX *= 0.85;
                if (attackFrame >= 22) {
                    state = State.IDLE;
                    attackFrame = 0;
                    hitLanded   = false;
                    attackCooldown = 50 + (int)(Math.random()*20);
                }
            } else if (state == State.WALK || state == State.IDLE) {
                thinkTimer--;
                if (thinkTimer <= 0) {
                    thinkTimer = 30 + (int)(Math.random()*30);

                    if (dist <= 60 && attackCooldown == 0) {
                        // attack range
                        state = State.ATTACK;
                        attackFrame = 0; hitLanded = false;
                    } else if (dist > 60) {
                        // chase
                        state = State.WALK;
                        velX = (dx > 0 ? 1 : -1) * (2.5 + Math.random());
                    } else {
                        // close but cooling down — shuffle
                        velX = (Math.random() < 0.5 ? 1 : -1) * 1.5;
                        state = State.WALK;
                    }

                    // random jump over player
                    if (dist < 120 && jumpCooldown == 0 && Math.random() < 0.3
                            && y >= groundY) {
                        velY = -11.5;
                        state = State.JUMP;
                        jumpCooldown = 80;
                    }
                }

                if (state == State.WALK) {
                    // keep moving toward player
                    double speed = 2.8 + Math.random() * 0.5;
                    velX = (dx > 0 ? 1 : -1) * speed;
                } else if (state == State.IDLE) {
                    velX *= 0.7;
                }
            } else if (state == State.JUMP) {
                velX = (dx > 0 ? 1 : -1) * 3.2;
                if (y >= groundY) state = State.IDLE;
            }

            checkHit(player);
        }

        // animation tick
        animTick++;
        int fps = (state == State.WALK) ? 5 : 8;
        if (animTick >= fps) {
            animTick = 0;
            int frames = (state == State.WALK) ? 4 : 1;
            animFrame = (animFrame + 1) % frames;
        }

        physicsStep(32, panelW - 32);
    }

    @Override
    void draw(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        if (!facingRight) {
            g2.translate((int)x * 2, 0);
            g2.scale(-1, 1);
        }
        int cx = (int)x, cy = (int)y;

        if (hurtFlash > 0 && hurtFlash % 4 < 2) {
            g2.setColor(new Color(255, 80, 80, 160));
            g2.fillRect(cx - W/2 - 2, cy - H - 2, W + 4, H + 4);
        }

        // create a dummy Fists for enemy
        Weapon fists = new Fists() {
            public int getRange() { return 36; }
        };
        Player.drawStickman(g2, cx, cy, state, attackFrame, animFrame, fists, false);
        g2.dispose();
        drawHealthBar(g, 44, 5);

        // "ENEMY" label
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(200, 80, 80, 180));
        g.drawString("ENEMY", (int)x - 16, (int)y - H - 10);
    }
}

// =====================================================================
//  Lab Background renderer
// =====================================================================
class LabBackground {

    private BufferedImage cache;
    private int cW, cH;

    BufferedImage get(int w, int h) {
        if (cache == null || cW != w || cH != h) {
            cW = w; cH = h;
            cache = build(w, h);
        }
        return cache;
    }

    private BufferedImage build(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int floorY = h - 100;

        // --- sky / wall gradient simulation (flat) ---
        g.setColor(new Color(10, 12, 22));
        g.fillRect(0, 0, w, floorY);

        // Back wall panels
        g.setColor(new Color(18, 22, 38));
        for (int px = 10; px < w - 10; px += 90) {
            g.fillRect(px, 20, 80, floorY - 30);
        }
        g.setColor(new Color(30, 36, 60));
        g.setStroke(new BasicStroke(1f));
        for (int px = 10; px < w - 10; px += 90) {
            g.drawRect(px, 20, 80, floorY - 30);
        }

        // horizontal pipe rows
        g.setColor(new Color(40, 50, 80));
        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(0, 80,  w, 80);
        g.drawLine(0, 180, w, 180);
        g.setColor(new Color(50, 62, 95));
        g.setStroke(new BasicStroke(5f));
        g.drawLine(0, 82,  w, 82);
        g.drawLine(0, 182, w, 182);

        // pipe bolts
        g.setColor(new Color(60, 75, 110));
        for (int bx = 30; bx < w; bx += 60) {
            g.fillOval(bx - 5, 76,  10, 10);
            g.fillOval(bx - 5, 176, 10, 10);
        }

        // glowing specimen tanks (left & right)
        drawTank(g, 60,  120, 60, 160, new Color(0, 200, 100, 120));
        drawTank(g, 150, 110, 60, 170, new Color(100, 0, 220, 100));
        drawTank(g, w - 130, 115, 60, 165, new Color(0, 140, 220, 110));
        drawTank(g, w - 60,  120, 55, 160, new Color(220, 80, 0, 100));

        // warning sign on wall
        drawWarningSign(g, w/2 - 50, 30, 100, 55);

        // "WING A7" stencil
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.setColor(new Color(60, 80, 120, 160));
        g.drawString("WING  A7", w/2 - 40, 24);

        // graffiti slogans
        g.setFont(new Font("Arial", Font.BOLD, 11));
        g.setColor(new Color(180, 30, 30, 180));
        g.drawString("FREEDOM IS NOT A SYMPTOM.", 20, floorY - 60);
        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(new Color(150, 150, 30, 160));
        g.drawString("THEY TRIED TO BREAK US.", w - 190, floorY - 70);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.setColor(new Color(60, 200, 80, 180));
        g.drawString("WE EVOLVE.", w - 170, floorY - 55);

        // monitor / screen on right wall
        drawMonitor(g, w - 110, floorY - 110, 90, 70);

        // "SYSTEM ONLINE" panel (left wall)
        drawSysPanel(g, 12, floorY - 120, 110, 90);

        // staircase silhouette right
        g.setColor(new Color(25, 30, 50));
        int sx = w - 100, sy = floorY - 10;
        for (int s = 0; s < 5; s++) {
            g.fillRect(sx + s * 14, sy - s * 18, 14 * (5 - s), 18 * (s + 1));
        }

        // floor
        g.setColor(new Color(22, 26, 42));
        g.fillRect(0, floorY, w, 100);
        g.setColor(new Color(30, 36, 58));
        // floor tiles
        g.setStroke(new BasicStroke(0.5f));
        for (int fx = 0; fx < w; fx += 50)
            for (int fy = floorY; fy < h; fy += 25) {
                g.setColor(new Color(35, 42, 65));
                g.drawRect(fx, fy, 50, 25);
            }

        // floor caution stripes near edges
        g.setStroke(new BasicStroke(4f));
        for (int cs = 0; cs < 8; cs++) {
            g.setColor(cs % 2 == 0 ? new Color(200, 160, 0, 120) : new Color(0, 0, 0, 60));
            g.fillRect(cs * 14, floorY, 14, 8);
            g.fillRect(w - 112 + cs * 14, floorY, 14, 8);
        }

        // "A7 SECTOR" floor text
        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        g.setColor(new Color(40, 50, 80, 140));
        g.drawString("A7  SECTOR", w/2 - 90, h - 20);

        // "EXPERIMENTATION BREEDS EVOLUTION" poster
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.setColor(new Color(180, 50, 50, 200));
        g.drawString("EXPERIMENTATION", w - 128, floorY - 35);
        g.drawString("BREEDS EVOLUTION", w - 128, floorY - 22);

        // EXIT sign
        drawExit(g, w/2 + 150, floorY - 5, 50, 20);

        g.dispose();
        return img;
    }

    private void drawTank(Graphics2D g, int cx, int y, int tw, int th, Color glow) {
        // glow aura
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 30));
        g.fillOval(cx - tw/2 - 15, y - 15, tw + 30, th + 30);
        // tank body
        g.setColor(new Color(20, 25, 45));
        g.fillRoundRect(cx - tw/2, y, tw, th, 10, 10);
        g.setColor(glow);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx - tw/2, y, tw, th, 10, 10);
        // liquid fill
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 50));
        g.fillRoundRect(cx - tw/2 + 3, y + th/3, tw - 6, th * 2/3 - 3, 8, 8);
        // stickman silhouette inside
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 160));
        int hx = cx, hy = y + th/3;
        g.setStroke(new BasicStroke(2f));
        g.drawOval(hx - 6, hy, 12, 12);
        g.drawLine(hx, hy + 12, hx, hy + 28);
        g.drawLine(hx, hy + 16, hx - 8, hy + 24);
        g.drawLine(hx, hy + 16, hx + 8, hy + 24);
        g.drawLine(hx, hy + 28, hx - 7, hy + 42);
        g.drawLine(hx, hy + 28, hx + 7, hy + 42);
        // highlight strip
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(cx - tw/2 + 4, y + 4, 8, th - 8, 4, 4);
    }

    private void drawWarningSign(Graphics2D g, int x, int y, int w, int h) {
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
        // screen content
        g.setColor(new Color(0, 180, 80, 200));
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.drawString("> SUBJECT DATA", x + 4, y + 14);
        g.drawString("> VITALS: OK",    x + 4, y + 24);
        g.drawString("> PHASE: COMBAT", x + 4, y + 34);
        g.drawString("> SECTOR: A7",    x + 4, y + 44);
        // scan lines
        g.setColor(new Color(0, 255, 100, 15));
        for (int sl = y; sl < y + h; sl += 3)
            g.drawLine(x, sl, x + w, sl);
    }

    private void drawSysPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(10, 40, 20, 200));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(0, 180, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(x, y, w, h);
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(new Color(0, 220, 80));
        g.drawString("SYSTEM ONLINE", x + 4, y + 14);
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.drawString("SUBJECTS: 0",   x + 4, y + 26);
        g.drawString("STATUS: STABLE", x + 4, y + 36);
        // blinking dot
        g.setColor(new Color(0, 255, 60));
        g.fillOval(x + 6, y + 46, 7, 7);
        // fake EKG line
        g.setColor(new Color(0, 200, 60, 180));
        g.setStroke(new BasicStroke(1.2f));
        int[] ekx = {x+4,  x+14, x+18, x+24, x+30, x+36, x+46, x+50, x+60, x+70, x+80, x+100};
        int[] eky = {y+70, y+70, y+58, y+78, y+60, y+75, y+75, y+60, y+80, y+65, y+75, y+75};
        g.drawPolyline(ekx, eky, ekx.length);
    }

    private void drawExit(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0, 160, 0, 180));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(0, 220, 0));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, w, h);
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(Color.WHITE);
        g.drawString("EXIT", x + 12, y + 13);
    }
}

// =====================================================================
//  Floating damage number
// =====================================================================
class DmgNumber {
    double x, y;
    int value;
    boolean crit;
    int life = 40;
    DmgNumber(double x, double y, int v, boolean c) {
        this.x = x; this.y = y; value = v; crit = c;
    }
    void update() { y -= 1.2; life--; }
    boolean dead() { return life <= 0; }
    void draw(Graphics2D g) {
        float alpha = life / 40f;
        g.setFont(new Font("Arial", Font.BOLD, crit ? 20 : 14));
        g.setColor(crit ? new Color(1f, 0.85f, 0.1f, alpha)
                        : new Color(1f, 0.4f, 0.4f, alpha));
        g.drawString((crit ? "★" : "-") + value, (int)x, (int)y);
    }
}

// =====================================================================
//  Main Game Panel
// =====================================================================
public class StickFightGame extends JPanel implements ActionListener, KeyListener {

    // ----- game states -----
    enum Screen { MENU, OPTIONS, PLAYING, RESULT }
    Screen screen = Screen.MENU;

    // ----- characters -----
    Player  player;
    Enemy   enemy;
    List<Character> chars = new ArrayList<>();

    // ----- background -----
    LabBackground lab = new LabBackground();

    // ----- HUD / effects -----
    List<DmgNumber> dmgNums = new ArrayList<>();
    String resultMsg = "";
    int    resultTimer = 0;
    int    screenShake = 0;

    // ----- menu animation -----
    float menuTick   = 0;
    int   menuPulse  = 0;
    boolean menuBlink = true;
    int blinkTimer = 0;

    // ----- input -----
    boolean left, right, up;

    // ----- timer -----
    Timer timer;

    static final int W = 800, H = 520, FLOOR_Y = 420;

    public StickFightGame() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(10, 12, 22));
        setFocusable(true);
        addKeyListener(this);
        timer = new Timer(16, this);
        timer.start();
    }

    void initGame() {
        player = new Player(160, FLOOR_Y, W);
        enemy  = new Enemy(600, FLOOR_Y, W);
        chars.clear();
        chars.add(player);
        chars.add(enemy);
        dmgNums.clear();
        resultMsg   = "";
        resultTimer = 0;
        screenShake = 0;
    }

    // =====================================================================
    //  ActionPerformed (game loop)
    // =====================================================================
    @Override
    public void actionPerformed(ActionEvent e) {
        menuTick += 0.04f;
        blinkTimer++;
        if (blinkTimer > 40) { menuBlink = !menuBlink; blinkTimer = 0; }

        if (screen == Screen.PLAYING) {
            gameLoop();
        }
        repaint();
    }

    void gameLoop() {
        // player input
        if (player.isAlive()) {
            if (left  && player.state != Player.State.ATTACK) {
                player.velX = -6.5;
                player.facingRight = false;
                if (player.state == Player.State.IDLE) player.state = Player.State.WALK;
            }
            if (right && player.state != Player.State.ATTACK) {
                player.velX = 6.5;
                player.facingRight = true;
                if (player.state == Player.State.IDLE) player.state = Player.State.WALK;
            }
            if (!left && !right && player.state == Player.State.WALK) {
                player.state = Player.State.IDLE;
                player.velX *= 0.7;
            }
            if (up && player.y >= player.groundY
                    && player.state != Player.State.ATTACK) {
                player.velY = -12;
                player.state = Player.State.JUMP;
                up = false;
            }
        }

        // old health for damage detection
        int prevEnemyHP  = enemy.health;
        int prevPlayerHP = player.health;

        // update all
        player.checkHit(enemy);
        for (Character c : chars) c.update(chars);

        // damage numbers
        int dd = prevEnemyHP - enemy.health;
        if (dd > 0) {
            dmgNums.add(new DmgNumber(enemy.x, enemy.y - enemy.H - 5, dd, dd >= 16));
            if (dd >= 16) screenShake = 8;
        }
        int dp = prevPlayerHP - player.health;
        if (dp > 0) {
            dmgNums.add(new DmgNumber(player.x, player.y - player.H - 5, dp, dp >= 12));
            if (dp >= 12) screenShake = 6;
        }

        dmgNums.removeIf(DmgNumber::dead);
        dmgNums.forEach(DmgNumber::update);

        if (screenShake > 0) screenShake--;

        // result check
        if (resultMsg.isEmpty()) {
            if (!enemy.isAlive()) {
                resultMsg = "VICTORY!";
                resultTimer = 180;
            } else if (!player.isAlive()) {
                resultMsg = "DEFEATED...";
                resultTimer = 180;
            }
        }
        if (resultTimer > 0) {
            resultTimer--;
            if (resultTimer == 0) screen = Screen.RESULT;
        }
    }

    // =====================================================================
    //  paintComponent
    // =====================================================================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        switch (screen) {
            case MENU:    drawMenu(g);    break;
            case OPTIONS: drawOptions(g); break;
            case PLAYING: drawGame(g);    break;
            case RESULT:  drawResult(g);  break;
        }
        g.dispose();
    }

    // =====================================================================
    //  MENU
    // =====================================================================
    void drawMenu(Graphics2D g) {
        // background
        g.drawImage(lab.get(W, H), 0, 0, null);

        // animated scanline overlay
        g.setColor(new Color(0, 0, 0, 60));
        for (int yl = 0; yl < H; yl += 3) g.drawLine(0, yl, W, yl);

        // vignette
        for (int i = 0; i < 60; i++) {
            int a = (int)(i * 1.8);
            g.setColor(new Color(0, 0, 0, a > 255 ? 255 : a));
            g.drawRect(i, i, W - i*2, H - i*2);
        }

        // title  "Project Adrenaline"
        Font titleFont = new Font("Serif", Font.BOLD | Font.ITALIC, 52);
        g.setFont(titleFont);
        String title = "Project Adrenaline";
        // subtle glow
        g.setColor(new Color(200, 120, 30, 60));
        g.drawString(title, 59, 321);
        g.setColor(new Color(235, 220, 190));
        g.drawString(title, 56, 318);

        // menu buttons
        int bx = 70, by = 345, bw = 175, bh = 38, gap = 12;
        drawMenuButton(g, "▶  Play",    bx, by,            bw, bh, Color.WHITE);
        drawMenuButton(g, "⚙  Options", bx, by + bh + gap, bw, bh, Color.WHITE);
        drawMenuButton(g, "✕  Exit",    bx, by + (bh+gap)*2, bw, bh, new Color(255, 160, 160));

        // bottom demo tag
        if (menuBlink) {
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(new Color(200, 100, 30, 200));
            g.drawString("—  DEMO VERSION  —  PROJECT ADRENALINE  —", 220, H - 12);
        }

        // controls hint
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(120, 140, 180, 180));
        g.drawString("PRESS  P  TO  PLAY  /  O  FOR  OPTIONS  /  ESC  TO  EXIT", 140, H - 28);
    }

    void drawMenuButton(Graphics2D g, String label, int x, int y, int w, int h, Color col) {
        g.setColor(new Color(200, 180, 120, 35));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(200, 180, 120, 90));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, w, h);
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        g.setColor(col);
        g.drawString(label, x + 16, y + h - 11);
    }

    // =====================================================================
    //  OPTIONS
    // =====================================================================
    void drawOptions(Graphics2D g) {
        g.drawImage(lab.get(W, H), 0, 0, null);
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, W, H);

        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 38));
        g.setColor(new Color(220, 200, 160));
        g.drawString("Options", 55, 90);

        g.setColor(new Color(200, 100, 30));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(55, 100, 420, 100);

        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(new Color(180, 180, 200));
        g.drawString("CONTROLS", 55, 132);

        String[] keys   = {"A  /  D",  "SPACE",       "J",        "K"};
        String[] actions= {"Move left / right","Jump","Attack","Switch weapon  (Fists / Sword)"};
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        for (int i = 0; i < keys.length; i++) {
            int ky = 160 + i * 32;
            g.setColor(new Color(230, 190, 80));
            g.drawString(keys[i],   65, ky);
            g.setColor(new Color(180, 200, 220));
            g.drawString("—  " + actions[i], 200, ky);
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.setColor(new Color(120, 160, 120));
        g.drawString("• Enemy AI attacks automatically when in range.", 65, 310);
        g.drawString("• Knockback increases with critical hits.", 65, 328);
        g.drawString("• Combo multiplier resets after 1.5 seconds.", 65, 346);

        drawMenuButton(g, "←  Back", 65, 400, 140, 38, new Color(200, 200, 220));
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(100, 100, 140));
        g.drawString("PRESS  O  TO  RETURN", 65, H - 12);
    }

    // =====================================================================
    //  GAME
    // =====================================================================
    void drawGame(Graphics2D g) {
        // screen shake
        if (screenShake > 0) {
            int dx = (int)((Math.random() - 0.5) * screenShake * 2);
            int dy = (int)((Math.random() - 0.5) * screenShake * 2);
            g.translate(dx, dy);
        }

        // lab background
        g.drawImage(lab.get(W, H), 0, 0, null);

        // shadow under characters
        for (Character c : chars) {
            g.setColor(new Color(0, 0, 0, 80));
            g.fillOval((int)c.x - 20, FLOOR_Y - 8, 40, 12);
        }

        // characters
        for (Character c : chars) c.draw(g);

        // damage numbers
        for (DmgNumber dn : dmgNums) dn.draw(g);

        // HUD bar
        drawHUD(g);

        // mid-fight result banner
        if (!resultMsg.isEmpty() && resultTimer > 0) {
            float alpha = Math.min(1f, (180 - resultTimer) / 15f);
            g.setColor(new Color(0, 0, 0, (int)(160 * alpha)));
            g.fillRect(0, 180, W, 100);
            g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 60));
            boolean victory = resultMsg.startsWith("V");
            g.setColor(new Color(victory ? 0.2f : 0.9f,
                                 victory ? 0.9f : 0.2f,
                                 0.2f, alpha));
            FontMetrics fm = g.getFontMetrics();
            int tx = (W - fm.stringWidth(resultMsg)) / 2;
            g.drawString(resultMsg, tx, 250);
        }

        // DEMO watermark
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(200, 100, 30, 120));
        g.drawString("DEMO — PROJECT ADRENALINE", W - 215, H - 8);
    }

    void drawHUD(Graphics2D g) {
        // player HP bar (left)
        drawBigBar(g, 20, 12, 200, 16,
                   player.health, player.maxHealth,
                   "PLAYER", new Color(50, 200, 80));

        // enemy HP bar (right)
        drawBigBar(g, W - 220, 12, 200, 16,
                   enemy.health, enemy.maxHealth,
                   "ENEMY",  new Color(200, 60, 60));

        // weapon indicator (center)
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.setColor(new Color(180, 200, 240, 200));
        String wp = "[ " + player.weapon.getName().toUpperCase() + " ]  K to switch";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(wp, (W - fm.stringWidth(wp)) / 2, 24);

        // controls reminder
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(100, 120, 160, 180));
        g.drawString("A/D move   SPACE jump   J attack", (W - 210) / 2, H - 8);
    }

    void drawBigBar(Graphics2D g, int x, int y, int bw, int bh,
                    int hp, int maxHp, String label, Color fill) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(x - 2, y - 2, bw + 4, bh + 4 + 14);
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(180, 190, 210));
        g.drawString(label, x, y + bh + 13);
        g.setColor(new Color(50, 0, 0));
        g.fillRect(x, y, bw, bh);
        float pct = hp / (float) maxHp;
        g.setColor(fill);
        g.fillRect(x, y, (int)(bw * pct), bh);
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(x, y, (int)(bw * pct), bh / 2);
        g.setColor(new Color(200, 200, 200, 80));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, bw, bh);
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(Color.WHITE);
        g.drawString(hp + " / " + maxHp, x + bw/2 - 18, y + bh - 2);
    }

    // =====================================================================
    //  RESULT SCREEN
    // =====================================================================
    void drawResult(Graphics2D g) {
        g.drawImage(lab.get(W, H), 0, 0, null);
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, W, H);

        boolean win = !enemy.isAlive();
        g.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 68));
        g.setColor(win ? new Color(60, 230, 100) : new Color(230, 60, 60));
        String msg = win ? "VICTORY!" : "DEFEATED...";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(msg, (W - fm.stringWidth(msg)) / 2, 220);

        if (win) {
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            g.setColor(new Color(200, 190, 120));
            String sub = "Combo max: " + player.comboCount + "  —  Subject neutralised.";
            fm = g.getFontMetrics();
            g.drawString(sub, (W - fm.stringWidth(sub)) / 2, 268);
        }

        drawMenuButton(g, "▶  Play Again", (W - 180) / 2,     320, 180, 44, Color.WHITE);
        drawMenuButton(g, "←  Main Menu",  (W - 180) / 2,     376, 180, 44, new Color(200,200,240));

        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(100, 100, 140));
        g.drawString("PRESS  R  TO  RETRY  /  M  FOR  MENU", (W - 270) / 2, H - 12);
    }

    // =====================================================================
    //  KEY EVENTS
    // =====================================================================
    @Override public void keyPressed(KeyEvent e) {
        int c = e.getKeyCode();
        switch (screen) {
            case MENU:
                if (c == KeyEvent.VK_P || c == KeyEvent.VK_ENTER) { initGame(); screen = Screen.PLAYING; }
                if (c == KeyEvent.VK_O) screen = Screen.OPTIONS;
                if (c == KeyEvent.VK_ESCAPE) System.exit(0);
                break;
            case OPTIONS:
                if (c == KeyEvent.VK_O || c == KeyEvent.VK_ESCAPE) screen = Screen.MENU;
                break;
            case PLAYING:
                if (c == KeyEvent.VK_A)     left  = true;
                if (c == KeyEvent.VK_D)     right = true;
                if (c == KeyEvent.VK_SPACE) up    = true;
                if (c == KeyEvent.VK_J)     player.startAttack();
                if (c == KeyEvent.VK_K) {
                    player.setWeapon(player.weapon instanceof Fists ? new Sword() : new Fists());
                }
                if (c == KeyEvent.VK_ESCAPE) screen = Screen.MENU;
                break;
            case RESULT:
                if (c == KeyEvent.VK_R) { initGame(); screen = Screen.PLAYING; }
                if (c == KeyEvent.VK_M || c == KeyEvent.VK_ESCAPE) screen = Screen.MENU;
                break;
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        int c = e.getKeyCode();
        if (c == KeyEvent.VK_A)     left  = false;
        if (c == KeyEvent.VK_D)     right = false;
        if (c == KeyEvent.VK_SPACE) up    = false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    // =====================================================================
    //  Mouse support for menu buttons
    // =====================================================================
    {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int mx = e.getX(), my = e.getY();
                if (screen == Screen.MENU) {
                    if (hit(mx, my, 70, 345, 175, 38))  { initGame(); screen = Screen.PLAYING; }
                    if (hit(mx, my, 70, 395, 175, 38))  screen = Screen.OPTIONS;
                    if (hit(mx, my, 70, 445, 175, 38))  System.exit(0);
                } else if (screen == Screen.OPTIONS) {
                    if (hit(mx, my, 65, 400, 140, 38))  screen = Screen.MENU;
                } else if (screen == Screen.RESULT) {
                    int bx = (W - 180) / 2;
                    if (hit(mx, my, bx, 320, 180, 44)) { initGame(); screen = Screen.PLAYING; }
                    if (hit(mx, my, bx, 376, 180, 44)) screen = Screen.MENU;
                }
                requestFocusInWindow();
            }
        });
    }
    boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // =====================================================================
    //  Main
    // =====================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Project Adrenaline  [DEMO]");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            StickFightGame game = new StickFightGame();
            frame.add(game);
            frame.pack();
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}