import { useState } from 'react';

export interface VideoUploaderHook {
  isUploading: boolean;
  videoUrl: string | null;
  uploadVideo: (file: File) => Promise<boolean>; // Visszaadja, hogy sikeres volt-e
}

export const useMockVideoUploader = (): VideoUploaderHook => {
  const [isUploading, setIsUploading] = useState(false);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);

  const uploadVideo = async (file: File) => {
    setIsUploading(true);
    try {
      await new Promise(resolve => setTimeout(resolve, 3000));
      
      // Megtisztítjuk a memóriát, ha volt már korábbi videó
      if (videoUrl) URL.revokeObjectURL(videoUrl);
      
      // Létrehozunk egy helyi linket a fájlból (így azonnal lejátszható)
      const objectUrl = URL.createObjectURL(file);
      setVideoUrl(objectUrl);
      
      return true; // Sikeres volt
    } catch (error) {
      console.error("Mock feltöltés hiba:", error);
      return false; // Hiba történt
    } finally {
      setIsUploading(false);
    }
  };

  return { isUploading, videoUrl, uploadVideo };
};

// 3. A VALÓDI (API) implementáció
export const useApiVideoUploader = (): VideoUploaderHook => {
  const [isUploading, setIsUploading] = useState(false);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);

  const uploadVideo = async (file: File) => {
    setIsUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file); 

      const response = await fetch('http://localhost:8080/api/matches/upload', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) throw new Error(`Szerver hiba történt: ${response.status}`);

      const data = await response.json();
      const streamUrl = data.videoUrl; 
      
      if (!streamUrl) throw new Error("A backend nem küldött vissza videoUrl-t!");

      setVideoUrl(streamUrl); 
      return true; // Sikeres
    } catch (error) {
      console.error("API feltöltés hiba:", error);
      alert("Nem sikerült a feltöltés. Ellenőrizd a backendet és a konzolt!");
      return false; // Hiba
    } finally {
      setIsUploading(false);
    }
  };

  return { isUploading, videoUrl, uploadVideo };
};