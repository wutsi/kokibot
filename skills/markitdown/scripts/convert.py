import cv2
import numpy as np
import pytesseract
from markitdown import MarkItDown, FileConverter


# 1. Define the Tesseract Logic as a MarkItDown-compatible Converter
class TesseractConverter(FileConverter):
    def convert(self, local_path, **kwargs):
        # 1. Read the image
        img = cv2.imread(local_path)
        if img is None:
            return "Error: Could not read image."

        # 2. Grayscale conversion
        # Essential for OCR as it simplifies the data to a single intensity channel.
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

        # 3. Noise Reduction (Bilateral Filtering)
        # Smooths flat areas while keeping edges sharp (better than Gaussian for OCR).
        denoised = cv2.bilateralFilter(gray, 9, 75, 75)

        # 4. Adaptive Thresholding
        # Handles uneven lighting by calculating thresholds for small pixel neighborhoods.
        processed = cv2.adaptiveThreshold(
            denoised, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
            cv2.THRESH_BINARY, 11, 2
        )

        # Extract text
        text = pytesseract.image_to_string(processed)
        return text.strip()


# 2. Initialize MarkItDown
markitdown = MarkItDown()

# 3. Register the Tesseract Converter for common image types
# This tells MarkItDown: "If you see these files, don't use an LLM, use Tesseract."
tesseract_handler = TesseractConverter()
markitdown.register_converter(tesseract_handler, [
    "image/jpeg",
    "image/png",
    "image/tiff",
    "image/bmp"
])


# 4. Use it!
def convert_to_md(file_path):
    # This will now use your Tesseract logic for images
    # and standard logic for PDF/Word/Excel.
    result = markitdown.convert(file_path)
    return result.text_content


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 convert.py <path_to_image>")
        sys.exit(1)

    print(convert_to_md(sys.argv[1]))
