# Possible values for `device` parameter can be found using `avdmanager list devices`
SCREENSHOT_DEVICES = [
  {
    device_type: 'phone',
    device: 'pixel_3',
    api: 31
  },
  {
    device_type: 'tenInch',
    device: 'Nexus 9',
    api: 31
  }
].freeze

SCREENSHOT_LOCALES = ALL_LOCALES
  .select { |hsh| hsh[:promo_config] != false }
  .map { |h| h[:google_play] }
  .compact
  .freeze

platform :android do
  # Takes screenshots for the WordPress or Jetpack app across multiple device and locales.
  # 
  # @option [String|Symbol] app The app to take screenshots for. Must be `wordpress` or `jetpack`
  # @option [String] device The device type to limit the build of screenshots for (e.g. `phone` or `tenInch`). Defaults to building all device types defined in `SCREENSHOT_DEVICES`.
  # @option [Array<String>] locale The Google Play locale code(s) to build the screenshots. Default to all the ones in `SCREENSHOT_LOCALES`.
  #
  desc "Build and capture raw screenshots"
  lane :screenshots do |options|
    app = get_app_name_option!(options)

    gradle(tasks: ["assemble#{app.to_s.capitalize}VanillaDebug", "assemble#{app.to_s.capitalize}VanillaDebugAndroidTest"])

    # Clear previous screenshots if we build for all devices. Don't clear if we only do a subset
    should_clear_previous_screenshots = options[:device].nil? && options[:locale].nil?

    # Allow creating screenshots for just one device type
    screenshot_devices = SCREENSHOT_DEVICES
    screenshot_devices = screenshot_devices.select { |device| device[:device_type].casecmp(options[:device]) == 0 } unless options[:device].nil?
    
    # Allow creating screenshots for just one locale
    locales = SCREENSHOT_LOCALES # TODO: Verify that we're using the expected locale code formats here (Google's `fr-FR` vs Android's `fr-rFR` or something else)
    locales = options[:locale].split(',') unless options[:locale].nil?
    
    UI.message("Will run screenshot for devices: #{screenshot_devices.map { |d| "#{d[:device]} API #{d[:api]}" }.inspect } and locales: #{locales.inspect}")

    apk_dir = File.join('WordPress', 'build', 'outputs', 'apk')
    package_name = APP_SPECIFIC_VALUES[app.to_sym][:package_name]
    test_class = APP_SPECIFIC_VALUES[app.to_sym][:screenshots_test_class]
    output_dir = screenshots_dir(app: app, subfolder: 'raw')

    screenshot_devices.each do |device|
      name = android_create_avd(device_model: device[:device], api_level: device[:api]) # Create the AVD for device, API and system image we need
      serial = android_launch_emulator(avd_name: name) # Launch an emulator using this AVD and get its serial number, to know which emulator to run the tests on

      capture_android_screenshots(
        app_apk_path: File.join(apk_dir, "#{app}Vanilla", 'debug', "org.wordpress.android-#{app}-vanilla-debug.apk"),
        tests_apk_path: File.join(apk_dir, 'androidTest', "#{app}Vanilla", 'debug', "org.wordpress.android-#{app}-vanilla-debug-androidTest.apk"),
        reinstall_app: false,
        clear_previous_screenshots: should_clear_previous_screenshots,
        app_package_name: package_name,
        tests_package_name: "#{package_name}.test",
        locales: locales,
        output_directory: output_dir,
        skip_open_summary: is_ci,
        use_tests_in_classes: test_class,
        test_instrumentation_runner: 'org.wordpress.android.WordPressTestRunner',
        specific_device: serial,
        device_type: device[:device_type],
        use_timestamp_suffix: false
      )

      android_shutdown_emulator(serials: [serial]) # Clean up after ourselves
    end
  end

  #####################################################################################
  # download_promo_strings
  # -----------------------------------------------------------------------------------
  # This lane download the translated promo strings from the translation system
  # -----------------------------------------------------------------------------------
  # Usage:
  # fastlane download_promo_strings
  #
  # Example:
  # fastlane download_promo_strings
  #####################################################################################
  desc "Downloads translated promo strings from the translation system"
  lane :download_promo_strings do |options|
    # FIXME: Make this app dependent instead (WordPress vs Jetpack)

    # "<key in .po file>" => { desc: "<name of txt file>" }
    files = (1..9).map do |n|
      ["play_store_screenshot_#{n}", { desc: "play_store_screenshot_#{n}.txt" }]
    end.to_h

    locales = ALL_LOCALES
      .select { |h| h[:promo_config] != false }
      .map { |h| [ h[:glotpress], h[:google_play] ]}

    gp_downloadmetadata(project_url: APP_SPECIFIC_VALUES[:wordpress][:glotpress_metadata_project],
      target_files: files,
      locales: locales,
      source_locale: "en-US",
      download_path: File.join(FASTLANE_FOLDER, 'playstoreres', 'metadata')
    )
  end


  #####################################################################################
  # download_raw_screenshots
  # -----------------------------------------------------------------------------------
  # This lane downloads the raw screenshots generated by a Firebase Test Lab run.
  # -----------------------------------------------------------------------------------
  # Usage:
  #   fastlane download_raw_screenshots app:<wordpress|jetpack> bucket:<gs-url> phone:<model-version> tenInch:<model-version>
  #
  # Example:
  #   fastlane download_raw_screenshots app:wordpress bucket:"gs://test-lab-some-id/yyyy-MM-dd_hh:mm:ss.fff_xyzt/" phone:blueline-28 tenInch:gts3lltevzw-28
  #
  # Notes:
  #   Screenshots generated by Firebase Test Lab are in JPG and have a long prefix in their basename. Those will automatically
  #   be converted to PNG and the long prefix will get removed when they get processed by `create_promo_screenshots` though.
  #####################################################################################
  desc "Download raw screenshots from Firebase Test Lab / Google Storage"
  lane :download_raw_screenshots do |options|
    app = get_app_name_option!(options)
    folder_mapping = { options[:phone] => 'phoneScreenshots', options[:tenInch] => 'tenInchScreenshots' }

    raw_screenshots_dir = screenshots_dir(app: app, subfolder: 'raw')
    FileUtils.rm_rf(raw_screenshots_dir)
    FileUtils.mkdir_p(raw_screenshots_dir)

    bucket_url = options[:bucket]
    device_dirs = `gsutil ls "#{bucket_url}"`
      .split("\n")
      .filter { |f| f.end_with?('/') }
      .sort

    gsutil_cp = ["gsutil", (is_ci? ? '-m' : nil), "cp", "-cU"].compact
    device_dirs.each do |device_dir|
      parts = File.basename(device_dir).split('-') # "model-api-locale-orientation[_rerunN]"
      model_and_api = parts[0..1].join('-')
      subdir = folder_mapping[model_and_api]
      next if subdir.nil?
      locale = parts[2].gsub('_','-')
      dest = File.join(raw_screenshots_dir, locale, 'images', subdir)
      FileUtils.mkdir_p(dest)
      sh(*gsutil_cp, File.join(device_dir, "artifacts/*.jpg"), dest) do |ok, res|
        UI.error("Failed to download artifacts for #{subdir} (status: #{res.exitstatus})") unless ok
      end
    end
  end


  #####################################################################################
  # create_promo_screenshots
  # -----------------------------------------------------------------------------------
  # This lane creates the promo screenshot from the original ones that
  # are taken by the screenshot lane
  # -----------------------------------------------------------------------------------
  # Usage:
  # fastlane create_promo_screenshots app:<wordpress|jetpack>
  #
  # Example:
  # fastlane create_promo_screenshots app:wordpress
  #####################################################################################
  desc "Creates promo screenshots"
  lane :create_promo_screenshots do |options|
    begin
      require 'rmagick'
    rescue LoadError => e
      UI.user_error!("The rmagick gem doesn't seem to be installed. Be sure to use `bundle install --with screenshots`.")
    end
    app = get_app_name_option!(options)

    raw_screenshots_dir = screenshots_dir(app: app, subfolder: 'raw')
    raw_screenshots_processing_dir = screenshots_dir(app: app, subfolder: 'raw_tmp')
    promo_screenshots_processing_dir = screenshots_dir(app: app, subfolder: 'promo_tmp')
    final_metadata_dir = File.join(FASTLANE_FOLDER, APP_SPECIFIC_VALUES[app.to_sym][:metadata_dir], 'android')

    # Clean temporary folder from previous runs
    FileUtils.rm_rf(promo_screenshots_processing_dir)

    # Create a copy of the files to work with – this ensures that if we're doing multiple
    # screenshot generation tasks close together, we can keep reusing the same source files
    FileUtils.rm_rf(raw_screenshots_processing_dir)
    FileUtils.copy_entry(raw_screenshots_dir, raw_screenshots_processing_dir)

    # For JPG files generated by Firebase Test Lab: Convert them to PNG and get rid of long prefix in name
    test_class = APP_SPECIFIC_VALUES[app.to_sym][:screenshots_test_class]
    Dir.glob(File.join(raw_screenshots_processing_dir, '**', '*.jpg')).each do |jpg_path|
      # Remove long package prefix + digit suffix inserted by FTL in screenshot file names...
      new_filename = File.basename(jpg_path, '.jpg')
      new_filename.gsub!(/#{test_class}-[^-]*-/, '')
      new_filename.gsub!(/-[0-9]+$/, '')
      new_filename += '.png'
      png_path = File.join( File.dirname(jpg_path), new_filename )
      # Convert to PNG
      UI.message("Converting JPG to PNG: #{png_path}...")
      image = Magick::Image.read(jpg_path).first
      image.format = "PNG"
      image.write(png_path)
      FileUtils.rm(jpg_path)
    end

    # For PNG files generated via screengrab: Remove the timestamps from filenames to make them easier to work with
    Dir.glob(File.join(raw_screenshots_processing_dir, '**', '*.png')).each do |entry|
      ext = File.extname(entry)
      newfilename = File.dirname(entry) + "/" + File.basename(entry, ext).split("_")[0] + ext
      File.rename( entry, newfilename.downcase )
    end

    locales = ALL_LOCALES
      .reject { |h| h[:google_play].nil? }
      .map { |h| h[:google_play] }

    # Allow creating promo screenshots for just one locale
    if options[:locale] != nil
      locales.keep_if { |locale|
        locale.casecmp(options[:locale]) == 0
      }
    end

    # Remove locales we're not interested in
    Pathname(raw_screenshots_processing_dir)
        .children
        .select(&:directory?)
        .reject { |dir| locales.include? File.basename(dir) }
        .each do |dir|
          FileUtils.rm_rf(dir)
        end

    # Run screenshots generator tool
    promo_screenshots(
      orig_folder: raw_screenshots_processing_dir,
      metadata_folder: final_metadata_dir,
      output_folder: promo_screenshots_processing_dir,
      force: options[:force],
    )

    # Remove old screenshots from `final_metadata_dir` subfolders of the targetted locales
    UI.message("Cleaning old promo screenshots from #{final_metadata_dir} for #{locales.count} selected locales...")
    locales.each do |locale|
      screenshot_files = Dir.glob("#{final_metadata_dir}/#{locale}/images/*/*.png")
      FileUtils.rm( screenshot_files )
    end

    # Finally move generated screenshots from `promo_screenshots_processing_dir` to `final_metadata_dir` subfolders
    relative_paths = Dir.chdir(promo_screenshots_processing_dir) { Dir.glob("*/images/*/*.png") }
    UI.message("Moving #{relative_paths.count} new promo screenshots to #{final_metadata_dir}...")
    relative_paths.each do |entry|
      old_path = File.join( promo_screenshots_processing_dir, entry )
      new_path = File.join( final_metadata_dir, entry )
      FileUtils.mkdir_p( File.dirname(new_path) )
      File.rename( old_path, new_path )
    end

    # Clean up the temp directories
    FileUtils.rm_rf(raw_screenshots_processing_dir)
    FileUtils.rm_rf(promo_screenshots_processing_dir)
  end


  #####################################################################################
  # upload_and_replace_screenshots_in_play_store
  # -----------------------------------------------------------------------------------
  # This lane uploads the screenshots in /metadata/android/{locale}/images to Play
  # Store and replaces the existing ones.
  # If a locale doesn't have any screenshots, it'll be skipped.
  # -----------------------------------------------------------------------------------
  # Usage:
  # bundle exec fastlane upload_and_replace_screenshots_in_play_store app:<wordpress|jetpack>
  #
  # Example:
  # bundle exec fastlane upload_and_replace_screenshots_in_play_store app:wordpress
  #####################################################################################
  desc 'Upload Screenshots to Play Store and Replaces the existing ones'
  lane :upload_and_replace_screenshots_in_play_store do |options|
    app = get_app_name_option!(options)
    package_name = APP_SPECIFIC_VALUES[app.to_sym][:package_name]
    metadata_dir = File.join(FASTLANE_FOLDER, APP_SPECIFIC_VALUES[app.to_sym][:metadata_dir], 'android')

    upload_to_play_store(
      package_name: package_name,
      metadata_path: metadata_dir,
      skip_upload_apk: true,
      skip_upload_aab: true,
      skip_upload_metadata: true,
      skip_upload_changelogs: true,
      skip_upload_images: true,
      skip_upload_screenshots: false,
      json_key: UPLOAD_TO_PLAY_STORE_JSON_KEY
    )
  end

  def screenshots_dir(app:, subfolder: nil)
    dirs = [FASTLANE_FOLDER, 'screenshots', app.to_s.downcase]
    dirs << subfolder unless subfolder.nil?
    File.join(*dirs)
  end
end
