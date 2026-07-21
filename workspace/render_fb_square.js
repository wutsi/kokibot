const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

(async () => {
  // Target Specification: facebook - feed (square)
  const width = 1080;
  const height = 1080;
  const uniqueId = 'fb_square_start_writing';

  // Raw slides data array structured directly from the user's input parameters
  const slidesData = [
    {
      hook: "Start the Writing Today!",
      headline: "",
      subHead: "",
      cta: "",
      bgPath: "workspace/tmp/bg_fb_square.jpg"
    }
  ];

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width, height } });
  const page = await context.newPage();

  // Ensure target output directory space exists
  const outputDir = path.resolve('output', `asset_${uniqueId}`);
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  // Sequentially process and capture every slide image asset in the payload
  for (let i = 0; i < slidesData.length; i++) {
    const slide = slidesData[i];
    const outputPath = path.join(outputDir, `slide_${i + 1}.png`);

    // Resolve background images into a bulletproof Base64 Data URL to bypass browser file security
    let bgStyle = '';
    if (slide.bgPath) {
      try {
        if (slide.bgPath.startsWith('http://') || slide.bgPath.startsWith('https://')) {
          // Keep web URL paths intact
          bgStyle = `background-image: url("${slide.bgPath}");`;
        } else {
          // Resolve relative or absolute local files, convert to data URI string
          const resolvedPath = path.resolve(__dirname, slide.bgPath);
          if (fs.existsSync(resolvedPath)) {
            const ext = path.extname(resolvedPath).replace('.', '') || 'png';
            const base64Data = fs.readFileSync(resolvedPath, { encoding: 'base64' });
            bgStyle = `background-image: url("data:image/${ext};base64,${base64Data}");`;
          }
        }
      } catch (err) {
        console.error(`Warning: Failed to process background image path: ${slide.bgPath}`, err);
      }
    }

    // Dynamic self-contained HTML layout generation with embedded asset content
    const htmlContent = `
    <!DOCTYPE html>
    <html>
    <head>
      <link href="https://fonts.googleapis.com/css2?family=Lora:wght@700;800&family=PT+Sans:wght@400;700&display=swap" rel="stylesheet">
      <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
          width: ${width}px;
          height: ${height}px;
          overflow: hidden;
          font-family: 'PT Sans', sans-serif;
          ${bgStyle}
          background-size: cover;
          background-position: center;
          position: relative;
        }
        .overlay {
          position: absolute;
          top: 0; left: 0; right: 0; bottom: 0;
          background: linear-gradient(135deg, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0.25) 100%);
        }
        .canvas-container {
          position: relative;
          z-index: 2;
          width: 100%;
          height: 100%;
          display: flex;
          flex-direction: column;
          justify-content: center;
          align-items: center;
          padding: 60px;
          text-align: center;
        }
        .hook {
          font-family: 'Lora', serif;
          font-size: 84px;
          font-weight: 800;
          color: #ffffff;
          line-height: 1.1;
          text-shadow: 0 4px 24px rgba(0,0,0,0.4);
          word-wrap: break-word;
          max-width: 900px;
        }
        .accent-bar {
          width: 120px;
          height: 8px;
          background-color: #1d7edf;
          margin: 32px auto 0;
          border-radius: 4px;
        }
      </style>
    </head>
    <body>
      <div class="overlay"></div>
      <div class="canvas-container">
        ${slide.hook ? `<div class="hook">${slide.hook}</div>` : ''}
        <div class="accent-bar"></div>
      </div>
    </body>
    </html>
    `;

    await page.setContent(htmlContent);
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: outputPath, type: 'png' });
  }

  await browser.close();

  // Unified application output logs
  if (slidesData.length === 1) {
    console.log(`Asset generated: ${path.join(outputDir, 'slide_1.png')}`);
  } else {
    console.log(`Carousel generated: ${slidesData.length} slides saved to ${outputDir}`);
  }
})();
