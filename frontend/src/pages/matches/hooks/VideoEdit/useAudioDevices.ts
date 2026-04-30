import { useState, useEffect } from 'react';

export function useAudioDevices(): MediaDeviceInfo[] {
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);

  useEffect(() => {
    const load = () => {
      navigator.mediaDevices.enumerateDevices().then((all) => {
        setDevices(all.filter((d) => d.kind === 'audioinput'));
      });
    };

    load();
    navigator.mediaDevices.addEventListener('devicechange', load);
    window.addEventListener('micpermissiongranted', load);

    return () => {
      navigator.mediaDevices.removeEventListener('devicechange', load);
      window.removeEventListener('micpermissiongranted', load);
    };
  }, []);

  return devices;
}
