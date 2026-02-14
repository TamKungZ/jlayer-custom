import subprocess
import os
import csv
import json
from pathlib import Path
from datetime import datetime

INPUT_DIR = ""
OUTPUT_DIR = "./output_test"
REPORT_FILE_TXT = "test_results.txt"
REPORT_FILE_CSV = "test_results.csv"
AUDIO_EXTENSIONS = {'.wav', '.flac', '.m4a', '.mp3'}

Path(OUTPUT_DIR).mkdir(parents=True, exist_ok=True)

def get_audio_info(file_path):
    """ใช้ ffprobe เพื่อดึงข้อมูล Sample Rate, Channels และ Format"""
    cmd = [
        'ffprobe', '-v', 'quiet', '-print_format', 'json', 
        '-show_streams', '-show_format', str(file_path)
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    data = json.loads(result.stdout)
    
    if 'streams' in data and len(data['streams']) > 0:
        s = data['streams'][0]
        sample_rate = f"{int(s.get('sample_rate', 0))/1000}kHz"
        channels = "Stereo" if s.get('channels') == 2 else "Mono"
        bit_depth = s.get('bits_per_sample', 'N/A')
        return f"{sample_rate}, {channels}, {bit_depth}bit"
    return "Unknown Info"

def run_test():
    files = [p for p in Path(INPUT_DIR).rglob('*') if p.suffix.lower() in AUDIO_EXTENSIONS]
    
    files = [f for f in files if "_cbr" not in f.name and "_vbr" not in f.name]

    if not files:
        print("No audio files found!")
        return

    with open(REPORT_FILE_CSV, mode='w', newline='', encoding='utf-8') as csv_file:
        csv_writer = csv.writer(csv_file)
        csv_writer.writerow(['Filename', 'Original_Info', 'CBR_Size_MB', 'VBR_Size_MB', 'Savings_Percent'])

        with open(REPORT_FILE_TXT, "w", encoding="utf-8") as txt_file:
            txt_file.write(f"Audio Test Report - {datetime.now()}\n")
            txt_file.write("-" * 60 + "\n")

            for audio_file in files:
                name = audio_file.name
                info = get_audio_info(audio_file)
                cbr_out = Path(OUTPUT_DIR) / f"{audio_file.stem}_cbr.mp3"
                vbr_out = Path(OUTPUT_DIR) / f"{audio_file.stem}_vbr.mp3"

                # FFmpeg Processing (Quiet mode)
                subprocess.run(['ffmpeg', '-y', '-i', str(audio_file), '-b:a', '320k', str(cbr_out)], capture_output=True)
                subprocess.run(['ffmpeg', '-y', '-i', str(audio_file), '-q:a', '0', str(vbr_out)], capture_output=True)

                # Get Sizes
                cbr_sz = os.path.getsize(cbr_out) / (1024 * 1024)
                vbr_sz = os.path.getsize(vbr_out) / (1024 * 1024)
                savings = ((cbr_sz - vbr_sz) / cbr_sz) * 100 if cbr_sz > 0 else 0

                # Write to TXT
                res_text = (f"File: {name}\n"
                            f" - Info: {info}\n"
                            f" - CBR: {cbr_sz:.2f} MB\n"
                            f" - VBR: {vbr_sz:.2f} MB\n"
                            f" - Saved: {savings:.1f}%\n\n")
                txt_file.write(res_text)
                print(res_text.strip())

                # Write to CSV
                csv_writer.writerow([name, info, f"{cbr_sz:.2f}", f"{vbr_sz:.2f}", f"{savings:.1f}%"])

    print(f"Reports saved to {REPORT_FILE_TXT} and {REPORT_FILE_CSV}")

if __name__ == "__main__":
    run_test()
