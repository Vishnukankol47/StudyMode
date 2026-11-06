import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTPUT_DIR = "softonic_screenshots"
os.makedirs(OUTPUT_DIR, exist_ok=True)
ARTIFACT_DIR = r"C:\Users\vkv39\.gemini\antigravity\brain\be015556-afdd-4579-8006-6a38e45cc522"

W, H = 1080, 1920

FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_REGULAR = r"C:\Windows\Fonts\segoeui.ttf"

def get_font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except:
        return ImageFont.load_default()

def draw_rounded_rect(draw, bbox, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(bbox, radius=radius, fill=fill, outline=outline, width=width)

def create_base_canvas(glow_color=(16, 185, 129)):
    base = Image.new("RGBA", (W, H), (10, 13, 14, 255))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    
    glow_draw.ellipse([-200, -200, W + 200, 600], fill=(glow_color[0], glow_color[1], glow_color[2], 30))
    glow_draw.ellipse([100, H - 700, W + 300, H + 300], fill=(glow_color[0], glow_color[1], glow_color[2], 25))
    
    glow = glow.filter(ImageFilter.GaussianBlur(120))
    return Image.alpha_composite(base, glow)

# ==========================================
# SCREENSHOT 1: MINDFUL REEL & SHORTS COUNTER
# ==========================================
def render_screenshot_1():
    img = create_base_canvas(glow_color=(74, 222, 128))
    draw = ImageDraw.Draw(img)
    
    # 1. Badge Pill
    draw_rounded_rect(draw, [W//2 - 180, 100, W//2 + 180, 155], radius=25, fill=(18, 38, 26, 255), outline=(74, 222, 128, 255), width=2)
    f_badge = get_font(FONT_BOLD, 20)
    draw.text((W//2, 128), "MINDFUL SCROLL COUNTER", font=f_badge, fill=(74, 222, 128, 255), anchor="mm")
    
    # 2. Main Title
    f_title = get_font(FONT_BOLD, 52)
    draw.text((W//2, 215), "Track Shorts & Reels Live", font=f_title, fill=(255, 255, 255, 255), anchor="mm")
    
    # 3. Subtitle
    f_sub = get_font(FONT_REGULAR, 26)
    draw.text((W//2, 280), "Real-time floating HUD prevents mindless doom-scrolling", font=f_sub, fill=(156, 163, 175, 255), anchor="mm")
    draw.text((W//2, 320), "Separate counters for YouTube & Instagram • Resets at 12 AM", font=f_sub, fill=(156, 163, 175, 255), anchor="mm")

    # 4. Device Mockup Outer Frame
    phone_x1, phone_y1 = 120, 390
    phone_x2, phone_y2 = W - 120, H - 70
    draw_rounded_rect(draw, [phone_x1 - 4, phone_y1 - 4, phone_x2 + 4, phone_y2 + 4], radius=56, fill=(30, 41, 59, 255))
    draw_rounded_rect(draw, [phone_x1, phone_y1, phone_x2, phone_y2], radius=52, fill=(15, 20, 25, 255))
    
    scr_x1, scr_y1 = phone_x1 + 14, phone_y1 + 14
    scr_x2, scr_y2 = phone_x2 - 14, phone_y2 - 14
    draw_rounded_rect(draw, [scr_x1, scr_y1, scr_x2, scr_y2], radius=40, fill=(18, 22, 28, 255))
    
    # Video preview gradient background (simulated rich video content)
    v_bg = Image.new("RGBA", (scr_x2 - scr_x1, scr_y2 - scr_y1), (0, 0, 0, 0))
    v_draw = ImageDraw.Draw(v_bg)
    for y in range(scr_y2 - scr_y1):
        r = int(25 + 30 * math.sin(y / 200.0))
        g = int(35 + 20 * math.cos(y / 250.0))
        b = int(50 + 40 * math.sin(y / 300.0))
        v_draw.line([(0, y), (scr_x2 - scr_x1, y)], fill=(r, g, b, 255))
    img.paste(v_bg, (scr_x1, scr_y1), v_bg)
    
    # Status bar inside phone
    f_status = get_font(FONT_BOLD, 20)
    draw.text((scr_x1 + 40, scr_y1 + 25), "9:41", font=f_status, fill=(255, 255, 255, 220), anchor="lm")
    draw.text((scr_x2 - 40, scr_y1 + 25), "5G  100%", font=f_status, fill=(255, 255, 255, 220), anchor="rm")
    
    # FLOATING PILL HUD OVERLAY (YouTube Shorts)
    hud_x1, hud_y1 = scr_x1 + 35, scr_y1 + 90
    hud_x2, hud_y2 = scr_x1 + 450, scr_y1 + 175
    draw_rounded_rect(draw, [hud_x1 - 4, hud_y1 - 4, hud_x2 + 4, hud_y2 + 4], radius=44, fill=(74, 222, 128, 50))
    draw_rounded_rect(draw, [hud_x1, hud_y1, hud_x2, hud_y2], radius=40, fill=(18, 20, 18, 245), outline=(74, 222, 128, 255), width=3)
    
    # Play Icon inside HUD
    draw.ellipse([hud_x1 + 20, hud_y1 + 18, hud_x1 + 68, hud_y1 + 66], fill=(239, 68, 68, 255))
    draw.polygon([(hud_x1 + 38, hud_y1 + 30), (hud_x1 + 38, hud_y1 + 54), (hud_x1 + 54, hud_y1 + 42)], fill=(255, 255, 255, 255))
    
    f_hud = get_font(FONT_BOLD, 27)
    draw.text((hud_x1 + 85, hud_y1 + 42), "12 Shorts • 18 min", font=f_hud, fill=(255, 255, 255, 255), anchor="lm")
    
    # Instagram Reels variant pill mockup
    hud2_x1, hud2_y1 = scr_x1 + 35, scr_y1 + 200
    hud2_x2, hud2_y2 = scr_x1 + 440, scr_y1 + 285
    draw_rounded_rect(draw, [hud2_x1 - 4, hud2_y1 - 4, hud2_x2 + 4, hud2_y2 + 4], radius=44, fill=(244, 63, 94, 50))
    draw_rounded_rect(draw, [hud2_x1, hud2_y1, hud2_x2, hud2_y2], radius=40, fill=(18, 20, 18, 245), outline=(244, 63, 94, 255), width=3)
    
    draw.ellipse([hud2_x1 + 20, hud2_y1 + 18, hud2_x1 + 68, hud2_y1 + 66], fill=(244, 63, 94, 255))
    draw.rectangle([hud2_x1 + 35, hud2_y1 + 31, hud2_x1 + 53, hud2_y1 + 53], outline=(255, 255, 255, 255), width=2)
    draw.polygon([(hud2_x1 + 41, hud2_y1 + 37), (hud2_x1 + 41, hud2_y1 + 47), (hud2_x1 + 49, hud2_y1 + 42)], fill=(255, 255, 255, 255))
    
    draw.text((hud2_x1 + 85, hud2_y1 + 42), "16 Reels • 24 min", font=f_hud, fill=(255, 255, 255, 255), anchor="lm")

    # Right side engagement buttons (vector icons)
    action_x = scr_x2 - 80
    
    # 1. Like (Heart)
    ay1 = scr_y1 + 450
    draw.ellipse([action_x - 10, ay1, action_x + 50, ay1 + 60], fill=(0, 0, 0, 140))
    draw.polygon([(action_x + 20, ay1 + 45), (action_x + 5, ay1 + 25), (action_x + 15, ay1 + 15), (action_x + 20, ay1 + 22), (action_x + 25, ay1 + 15), (action_x + 35, ay1 + 25)], fill=(244, 63, 94, 255))
    draw.text((action_x + 20, ay1 + 75), "42K", font=get_font(FONT_REGULAR, 17), fill=(255, 255, 255, 220), anchor="mm")

    # 2. Comment (Bubble)
    ay2 = scr_y1 + 560
    draw.ellipse([action_x - 10, ay2, action_x + 50, ay2 + 60], fill=(0, 0, 0, 140))
    draw.rounded_rectangle([action_x + 6, ay2 + 18, action_x + 34, ay2 + 38], radius=6, fill=(255, 255, 255, 255))
    draw.polygon([(action_x + 12, ay2 + 38), (action_x + 18, ay2 + 45), (action_x + 20, ay2 + 38)], fill=(255, 255, 255, 255))
    draw.text((action_x + 20, ay2 + 75), "1.2K", font=get_font(FONT_REGULAR, 17), fill=(255, 255, 255, 220), anchor="mm")

    # 3. Share (Arrow)
    ay3 = scr_y1 + 670
    draw.ellipse([action_x - 10, ay3, action_x + 50, ay3 + 60], fill=(0, 0, 0, 140))
    draw.polygon([(action_x + 20, ay3 + 16), (action_x + 32, ay3 + 28), (action_x + 24, ay3 + 28), (action_x + 24, ay3 + 42), (action_x + 16, ay3 + 42), (action_x + 16, ay3 + 28), (action_x + 8, ay3 + 28)], fill=(255, 255, 255, 255))
    draw.text((action_x + 20, ay3 + 75), "Share", font=get_font(FONT_REGULAR, 17), fill=(255, 255, 255, 220), anchor="mm")

    # Video Info at Bottom
    f_creator = get_font(FONT_BOLD, 26)
    draw.text((scr_x1 + 45, scr_y2 - 340), "@ScientificMethod", font=f_creator, fill=(255, 255, 255, 255), anchor="lm")
    f_caption = get_font(FONT_REGULAR, 22)
    draw.text((scr_x1 + 45, scr_y2 - 295), "5 physics experiments you can test at home #learn", font=f_caption, fill=(220, 220, 220, 255), anchor="lm")

    # Callout Features List Card at Bottom of Screen
    card_y = scr_y2 - 240
    draw_rounded_rect(draw, [scr_x1 + 25, card_y, scr_x2 - 25, card_y + 190], radius=24, fill=(15, 23, 42, 245), outline=(74, 222, 128, 160), width=1)
    
    f_card_h = get_font(FONT_BOLD, 21)
    f_card_sub = get_font(FONT_REGULAR, 17)
    draw.text((scr_x1 + 50, card_y + 35), "• Accurate 1-to-1 Swipe Counting", font=f_card_h, fill=(74, 222, 128, 255), anchor="lm")
    draw.text((scr_x1 + 70, card_y + 65), "Debounced algorithms guarantee zero double-counting.", font=f_card_sub, fill=(203, 213, 225, 255), anchor="lm")
    
    draw.text((scr_x1 + 50, card_y + 110), "• Platform Isolation & Midnight Reset", font=f_card_h, fill=(74, 222, 128, 255), anchor="lm")
    draw.text((scr_x1 + 70, card_y + 140), "Instagram & YouTube track separately and reset at 12 AM.", font=f_card_sub, fill=(203, 213, 225, 255), anchor="lm")

    return img

print("Rendering perfected Softonic screenshot 1...")
s1 = render_screenshot_1()
s1.save(os.path.join(OUTPUT_DIR, "screenshot_1_reel_counter.png"))
s1.save(os.path.join(ARTIFACT_DIR, "screenshot_1_reel_counter.png"))
print("Screenshot 1 perfected!")
