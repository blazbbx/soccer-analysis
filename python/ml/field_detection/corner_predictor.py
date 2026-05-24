import numpy as np

class CornerPredictor:
    def __init__(self, coefficients: list, zoom_factor: float = 1.0):
        """
        Initializes the Defisheyer with distortion coefficients and zoom.
        
        Args:
            coefficients (list): The D array values, e.g., [k1, k2, k3, k4].
            zoom_factor (float): Zoom adjustment to crop black edges. Default is 1.0.
        """
        self.D = np.array(coefficients, dtype=np.float32)
        self.zoom_factor = zoom_factor
        
        # Placeholders for the pre-computed maps
        self.map1 = None
        self.map2 = None