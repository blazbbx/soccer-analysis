import numpy as np
import cv2

class MinimapCalculator:
    # source: A képen a 4 sarok koordinátái
    # target: a 
    def __init__(self, source: np.ndarray, minimap_size: tuple[int, int]):
        """
        Initializes MinimapCalculator with source and target
        
        Args:
            source (ndarray): the coordinates of the 4 corners on image (starting from top-left, clockwise. Can be out of bounds).
            target (tuple[int, int]): the minimap width, height (should be pixel ize).
        """
        source = source.astype(np.float32)
        target = np.array([[0,0], [minimap_size[0], 0], [minimap_size[0], minimap_size[1]], [0, minimap_size[1]]])
        target = target.astype(np.float32)
        self.m = cv2.getPerspectiveTransform(source, target)

    def transform_points(self, points: np.ndarray) -> np.ndarray:
        reshaped_points = points.reshape(-1, 1, 2).astype(np.float32)
        transformed_points = cv2.perspectiveTransform(reshaped_points, self.m)
        return transformed_points.reshape(-1, 2)