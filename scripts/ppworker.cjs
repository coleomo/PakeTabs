const fs = require('fs-extra')
const path = require('path')
const { execSync } = require('child_process')
const ppconfig = require('./ppconfig.json')

// update package.json build productName

function generateAdaptiveIcons(input, output) {
    const densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }

    // icon背景颜色,可设置为none透明
    const bgColor = '#FFFFFF'
    // 一般0.75, 前景最大占比（安全区）
    const foregroundScale = 0.68

    if (!fs.existsSync(output)) {
        fs.mkdirSync(output, { recursive: true })
    }

    for (const [folder, size] of Object.entries(densities)) {
        const dir = path.join(output, folder)
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })

        const backgroundFile = path.join(dir, 'ic_launcher_background.png')
        const foregroundFile = path.join(dir, 'ic_launcher_foreground.png')

        // linux只能convert， 背景：纯色填充（全覆盖）
        execSync(
            `convert -size ${size}x${size} canvas:"${bgColor}" ${backgroundFile}`
        )

        // 前景大小 = 图标尺寸 × 0.75
        const fgSize = Math.round(size * foregroundScale)

        // 前景：缩放到安全区域，居中，四周自动留边
        execSync(
            `convert "${input}" -resize ${fgSize}x${fgSize} ` +
                `-gravity center -background none -extent ${size}x${size} ${foregroundFile}`
        )
    }

    // 生成 Adaptive Icon XML (放到 mipmap-anydpi-v26)
    const anydpiDir = path.join(output, 'mipmap-anydpi-v26')
    if (!fs.existsSync(anydpiDir)) fs.mkdirSync(anydpiDir, { recursive: true })

    const xml = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>`

    fs.writeFileSync(path.join(anydpiDir, 'ic_launcher.xml'), xml, 'utf-8')

    console.log('✅ Adaptive Icons 已生成:', output)
}

const updateAppName = async (androidResDir, appName) => {
    // workerflow build apk name always is app-debug.apk
    try {
        const stringsPath = path.join(androidResDir, 'values', 'strings.xml')

        // Check if strings.xml exists
        const exists = await fs.pathExists(stringsPath)
        if (!exists) {
            console.log('⚠️ strings.xml not found, creating a new one')
            await fs.ensureDir(path.dirname(stringsPath))
            await fs.writeFile(
                stringsPath,
                `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${appName}</string>
</resources>`
            )
            console.log(`✅ Created strings.xml with app_name: ${appName}`)
            return
        }

        // Read and update existing strings.xml
        let content = await fs.readFile(stringsPath, 'utf8')

        // Check if app_name already exists
        if (content.includes('<string name="app_name">')) {
            content = content.replace(
                /<string name="app_name">.*?<\/string>/,
                `<string name="app_name">${appName}</string>`
            )
        } else {
            // Add app_name if it doesn't exist
            content = content.replace(
                /<\/resources>/,
                `    <string name="app_name">${appName}</string>\n</resources>`
            )
        }

        await fs.writeFile(stringsPath, content)
        console.log(`✅ Updated app_name to: ${appName}`)
    } catch (error) {
        console.error('❌ Error updating app name:', error)
    }
}

// update web url
const updateUrlPassword = async (androidResDir, url, password) => {
    try {
        // Assuming MainActivity.kt is in the standard location
        const mainActivityPath = path.join(
            androidResDir.replace('res', ''),
            'java/com/pakeandroid/paketabs/MainActivity.kt'
        )

        // Check if file exists
        const exists = await fs.pathExists(mainActivityPath)
        if (!exists) {
            console.log(
                '⚠️ MainActivity.kt not found at expected location:',
                mainActivityPath
            )
            return
        }

        // Read and update the file
        let content = await fs.readFile(mainActivityPath, 'utf8')

        // Replace the web URL in the loadUrl call
        let updatedContent = content.replace(
            /private const val DEFAULT_HOME_URL = ".*?"/,
            `private const val DEFAULT_HOME_URL = "${url}"`
        )
        updatedContent = updatedContent.replace(
            /private const val EXIT_PASSWORD = ".*?"/,
            `private const val EXIT_PASSWORD = "${password}"`
        )
        await fs.writeFile(mainActivityPath, updatedContent)
        console.log(
            `✅ Updated web url to: ${url} and password to: ${password}`
        )
    } catch (error) {
        console.error('❌ Error updating web url:', error)
    }
}

// Main execution
const main = async () => {
    console.log('🚀 worker start')
    const { name, url, password, input, output, copyTo, androidResDir } =
        ppconfig
    // generate adaptive icons
    const outPath = path.resolve(output)
    generateAdaptiveIcons(input, outPath)
    // copy icons to android res dir
    const dest = path.resolve(copyTo)
    await fs.copy(outPath, dest, { overwrite: true })
    console.log(`📦 Icons copied to Android res dir: ${dest}`)
    // update app name
    await updateAppName(androidResDir, name)
    console.log('🚀 worker end')
    // update web url and password
    await updateUrlPassword(androidResDir, url, password)
    console.log(`✅ Updated web url to: ${url} and password to: ${password}`)
}

// run worker
main()
