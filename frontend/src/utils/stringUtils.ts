export const getInitials = (name: string | undefined): string => {
  if (!name) return ''; 
  
  return name
    .trim() 
    .split(' ') 
    .filter(word => word.length > 0) 
    .map(word => word[0].toUpperCase()) 
    .join('') 
    .substring(0, 2); 
};